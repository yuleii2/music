package com.k2.music.song

import com.k2.music.CapoAssistant
import com.k2.music.ChordRepository
import com.k2.music.ChordTransposer
import com.k2.music.MusicTheoryUtils
import com.k2.music.PracticePreferences
import com.k2.music.TransitionAttempt
import com.k2.music.Voicing
import com.k2.music.VoicingRecommendationEngine
import com.k2.music.VoicingRecommendationMode
import com.k2.music.VoicingTransitionScorer
import kotlin.math.roundToInt

data class SongVoicingChoice(
    val id: String,
    val name: String,
    val barre: Boolean,
    val simplified: Boolean,
    val maxFret: Int,
    val familiar: Boolean,
    val pinned: Boolean,
)

data class SongRenderedChord(
    val eventId: String,
    val sectionId: String,
    val rowId: String,
    val sourceChord: String,
    val soundingChord: String,
    val shapeChord: String,
    val voicing: SongVoicingChoice?,
    val availableVoicings: List<SongVoicingChoice>,
    val warning: String?,
)

data class SongCapoPlan(
    val capoFret: Int,
    val shapes: List<String>,
    val barreChordCount: Int,
    val unfamiliarChordCount: Int,
    val highestFret: Int,
    val averageMastery: Int?,
    val deterministicScore: Int,
    val reason: String,
)

data class SongArrangement(
    val soundingKey: String,
    val shapeKey: String,
    val transposeSemitones: Int,
    val capoFret: Int,
    val accidentalPreference: MusicTheoryUtils.AccidentalPreference,
    val renderedChords: List<SongRenderedChord>,
    val capoPlans: List<SongCapoPlan>,
) {
    val missingVoicingCount: Int get() = renderedChords.count { it.voicing == null }
    val barreChordCount: Int get() = renderedChords.count { it.voicing?.barre == true }
    val warnings: List<String> get() = renderedChords.mapNotNull { it.warning }.distinct()
}

/** Pure, deterministic song-level transposition/capo/voicing orchestration over the existing core. */
class SongArrangementEngine(
    private val repository: ChordRepository,
    private val transposer: ChordTransposer = ChordTransposer(),
    private val capoAssistant: CapoAssistant = CapoAssistant(transposer),
    private val recommendationEngine: VoicingRecommendationEngine = VoicingRecommendationEngine(),
    private val extraVoicings: (String) -> List<Voicing> = { emptyList() },
) {
    fun arrange(
        project: SongProject,
        preferences: PracticePreferences,
        favorites: Set<String> = emptySet(),
        attempts: List<TransitionAttempt> = emptyList(),
    ): SongArrangement {
        val preference = project.accidentalPreference
        val eventContexts = orderedEvents(project)
        val sounding = eventContexts.map { context ->
            transposer.transposeChord(context.event.normalizedChordSymbol, project.transposeSemitones, preference)
        }
        val shapes = sounding.map { chord ->
            capoAssistant.shapeForSoundingChord(chord, project.capoFret, preference)
        }
        val rendered = eventContexts.mapIndexed { index, context ->
            val candidates = candidates(shapes[index])
            val choices = choices(shapes[index], candidates, preferences, context.event.selectedVoicingId)
            val pinned = context.event.selectedVoicingId?.let { selected -> choices.firstOrNull { it.id == selected } }
            val fallback = pinned ?: recommendedChoice(shapes[index], candidates, preferences, choices)
            val warning = when {
                context.event.selectedVoicingId != null && pinned == null ->
                    "${context.event.chordSymbol} 的固定指法已不存在或不适用于当前手型，已回退到本地推荐。"
                fallback == null -> "${shapes[index]} 暂无符合当前限制的本地指法。"
                else -> null
            }
            SongRenderedChord(
                eventId = context.event.id,
                sectionId = context.sectionId,
                rowId = context.rowId,
                sourceChord = context.event.chordSymbol,
                soundingChord = sounding[index],
                shapeChord = shapes[index],
                voicing = fallback,
                availableVoicings = choices,
                warning = warning,
            )
        }
        return SongArrangement(
            soundingKey = soundingKey(project),
            shapeKey = shapeKey(project),
            transposeSemitones = project.transposeSemitones,
            capoFret = project.capoFret,
            accidentalPreference = preference,
            renderedChords = rendered,
            capoPlans = recommendCapos(sounding, preferences, favorites, attempts, preference),
        )
    }

    fun configure(
        project: SongProject,
        transposeSemitones: Int,
        capoFret: Int,
        accidentalPreference: MusicTheoryUtils.AccidentalPreference,
        now: Long,
    ): SongProject = project.copy(
        transposeSemitones = transposeSemitones,
        capoFret = capoFret,
        accidentalPreference = accidentalPreference,
        updatedAt = now.coerceAtLeast(project.createdAt),
    )

    fun reset(project: SongProject, now: Long): SongProject = configure(
        project,
        transposeSemitones = 0,
        capoFret = 0,
        accidentalPreference = MusicTheoryUtils.AccidentalPreference.AUTO,
        now = now,
    )

    fun pinVoicing(project: SongProject, eventId: String, voicingId: String?, now: Long): SongProject {
        val context = orderedEvents(project).firstOrNull { it.event.id == eventId }
            ?: throw IllegalArgumentException("找不到要固定指法的和弦事件。")
        val sounding = transposer.transposeChord(
            context.event.normalizedChordSymbol,
            project.transposeSemitones,
            project.accidentalPreference,
        )
        val shape = capoAssistant.shapeForSoundingChord(sounding, project.capoFret, project.accidentalPreference)
        val normalizedId = voicingId?.trim()?.ifBlank { null }
        if (normalizedId != null) {
            require(candidates(shape).any { VoicingRecommendationEngine.voicingId(shape, it) == normalizedId }) {
                "所选固定指法不存在或不适用于当前和弦手型。"
            }
        }
        val sections = project.sections.map { section ->
            section.copy(
                rows = section.rows.map { row ->
                    row.copy(
                        chordEvents = row.chordEvents.map { event ->
                            if (event.id == eventId) event.copy(selectedVoicingId = normalizedId) else event
                        },
                    )
                },
            )
        }
        return project.copy(sections = sections, updatedAt = now.coerceAtLeast(project.createdAt))
    }

    private fun recommendCapos(
        soundingChords: List<String>,
        preferences: PracticePreferences,
        favorites: Set<String>,
        attempts: List<TransitionAttempt>,
        accidentalPreference: MusicTheoryUtils.AccidentalPreference,
    ): List<SongCapoPlan> {
        if (soundingChords.isEmpty()) return emptyList()
        val favoriteNormalized = favorites.mapNotNull(::normalize).toSet()
        val mastery = masteryByTransition(attempts)
        return (0..12).mapNotNull { capo ->
            val shapes = soundingChords.map {
                capoAssistant.shapeForSoundingChord(it, capo, accidentalPreference)
            }
            val theoreticallyValid = shapes.indices.all { index ->
                normalize(capoAssistant.soundingChord(shapes[index], capo, accidentalPreference)) ==
                    normalize(soundingChords[index])
            }
            if (!theoreticallyValid) return@mapNotNull null
            val candidatesByChord = shapes.distinct().associateWith(::candidates)
            val mode = if (preferences.proficiency == PracticePreferences.Proficiency.BEGINNER) {
                VoicingRecommendationMode.BEGINNER
            } else {
                VoicingRecommendationMode.AUTO
            }
            val recommendations = recommendationEngine.recommend(shapes, candidatesByChord, mode, preferences)
            if (recommendations.size != shapes.size) return@mapNotNull null
            val uniqueRecommendations = recommendations.distinctBy { it.chordSymbol }
            val barreCount = uniqueRecommendations.count { VoicingTransitionScorer.isBarre(it.voicing) }
            val unfamiliar = uniqueRecommendations.count { it.voicingId !in preferences.familiarVoicingIds }
            val highestFret = uniqueRecommendations.maxOfOrNull { VoicingTransitionScorer.maxFret(it.voicing) } ?: 0
            val favoriteCount = shapes.distinct().count { normalize(it) in favoriteNormalized }
            val transitionScores = shapes.zipWithNext()
                .filter { (from, to) -> normalize(from) != normalize(to) }
                .mapNotNull { (from, to) -> mastery[normalize(from) to normalize(to)] }
            val averageMastery = transitionScores.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            val simplifiedCount = uniqueRecommendations.count { it.voicing.simplified }
            val openCount = uniqueRecommendations.count { !VoicingTransitionScorer.isBarre(it.voicing) && it.voicing.frets.any { fret -> fret == 0 } }
            val score = barreCount * 30 + unfamiliar * 8 + highestFret * 2 +
                (capo - 7).coerceAtLeast(0) * 5 - favoriteCount * 3 -
                (averageMastery ?: 0) / 10 -
                if (preferences.proficiency == PracticePreferences.Proficiency.BEGINNER) simplifiedCount * 6 + openCount * 3 else 0
            val reason = buildList {
                add(if (barreCount == 0) "无需横按" else "$barreCount 个手型需要横按")
                add("最高使用 $highestFret 品")
                add(if (unfamiliar == 0) "均为已熟悉按法" else "$unfamiliar 个按法尚未标记熟悉")
                averageMastery?.let { add("相关方向切换熟练度约 $it 分") }
                if (capo > 7) add("变调夹位置较高")
            }.joinToString("；", postfix = "。")
            SongCapoPlan(
                capoFret = capo,
                shapes = shapes.distinct(),
                barreChordCount = barreCount,
                unfamiliarChordCount = unfamiliar,
                highestFret = highestFret,
                averageMastery = averageMastery,
                deterministicScore = score,
                reason = reason,
            )
        }.sortedWith(compareBy<SongCapoPlan> { it.deterministicScore }.thenBy { it.capoFret }).take(3)
    }

    private fun candidates(shape: String): List<Voicing> {
        val lookup = repository.find(shape)
        if (!lookup.recognized) return emptyList()
        return (lookup.chord.voicings + extraVoicings(shape))
            .distinctBy { VoicingRecommendationEngine.voicingId(shape, it) }
    }

    private fun choices(
        shape: String,
        candidates: List<Voicing>,
        preferences: PracticePreferences,
        pinnedId: String?,
    ): List<SongVoicingChoice> = candidates.map { voicing ->
        val id = VoicingRecommendationEngine.voicingId(shape, voicing)
        SongVoicingChoice(
            id = id,
            name = voicing.name,
            barre = VoicingTransitionScorer.isBarre(voicing),
            simplified = voicing.simplified,
            maxFret = VoicingTransitionScorer.maxFret(voicing),
            familiar = id in preferences.familiarVoicingIds,
            pinned = id == pinnedId,
        )
    }.sortedWith(
        compareByDescending<SongVoicingChoice> { it.pinned }
            .thenByDescending { it.familiar }
            .thenBy { it.barre }
            .thenByDescending { it.simplified }
            .thenBy { it.maxFret }
            .thenBy { it.id },
    )

    private fun recommendedChoice(
        shape: String,
        candidates: List<Voicing>,
        preferences: PracticePreferences,
        choices: List<SongVoicingChoice>,
    ): SongVoicingChoice? {
        val mode = if (preferences.proficiency == PracticePreferences.Proficiency.BEGINNER) {
            VoicingRecommendationMode.BEGINNER
        } else {
            VoicingRecommendationMode.AUTO
        }
        val recommendation = recommendationEngine.recommendNext(shape, candidates, null, mode, preferences)
        return recommendation?.let { result -> choices.firstOrNull { it.id == result.voicingId } }
    }

    private fun soundingKey(project: SongProject): String {
        if (project.originalKey.isBlank()) return "未设置"
        return runCatching {
            transposer.transposeChord(project.originalKey, project.transposeSemitones, project.accidentalPreference)
        }.getOrElse { "无法计算" }
    }

    private fun shapeKey(project: SongProject): String {
        val sounding = soundingKey(project)
        if (sounding == "未设置" || sounding == "无法计算") return sounding
        return runCatching {
            capoAssistant.shapeForSoundingChord(sounding, project.capoFret, project.accidentalPreference)
        }.getOrElse { "无法计算" }
    }

    private fun masteryByTransition(attempts: List<TransitionAttempt>): Map<Pair<String?, String?>, Int> = attempts
        .groupBy { normalize(it.fromChord) to normalize(it.toChord) }
        .mapValues { (_, values) ->
            val recent = values.sortedByDescending { it.timestampEpochMillis }.take(20)
            (recent.count { it.success }.toDouble() / recent.size * 100.0).roundToInt()
        }

    private fun normalize(symbol: String): String? {
        val parsed = repository.nameParser.parse(symbol)
        return parsed.normalizedSymbol.takeIf { parsed.recognized }
    }

    private fun orderedEvents(project: SongProject): List<EventContext> = project.sections
        .sortedBy { it.order }
        .flatMap { section ->
            section.rows.sortedBy { it.order }.flatMap { row ->
                row.chordEvents.sortedBy { it.order }.map { EventContext(section.id, row.id, it) }
            }
        }

    private data class EventContext(val sectionId: String, val rowId: String, val event: SongChordEvent)
}

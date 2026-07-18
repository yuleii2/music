package com.k2.music.ui.gateway

import com.k2.music.PracticePreferences
import com.k2.music.PracticePreferencesStore
import com.k2.music.LastPracticeConfig
import com.k2.music.LastPracticeConfigStore
import com.k2.music.PracticeRecordStore
import com.k2.music.PracticeSession
import com.k2.music.PracticeSummary
import com.k2.music.TimeSignature
import com.k2.music.TransitionAttempt
import com.k2.music.TransitionAttemptStore
import com.k2.music.ui.model.ProgressionUiModel
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.learning.DailyPracticePlan
import com.k2.music.ui.learning.DailyPracticePlanner
import com.k2.music.ui.learning.DefaultDailyPracticePlanner
import com.k2.music.ui.learning.LearningProfile
import java.util.UUID
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.TimeZone
import com.k2.music.ui.song.SongGateway
import com.k2.music.song.SongPracticeMode
import com.k2.music.song.SongTransition

enum class PracticeModeUi(val label: String) {
    TWO_CHORD("双和弦"),
    MULTI_CHORD("多和弦"),
    RANDOM("随机换和弦"),
}

enum class PracticeSwitchUi(val label: String) {
    EACH_BEAT("每拍切换"),
    EACH_MEASURE("每小节切换"),
}

data class PracticeConfigUi(
    val mode: PracticeModeUi = PracticeModeUi.TWO_CHORD,
    val symbols: String = "C G",
    val durationSeconds: Int = 60,
    val bpm: Int = 80,
    val timeSignature: String = "4/4",
    val switchMode: PracticeSwitchUi = PracticeSwitchUi.EACH_MEASURE,
    val accentFirstBeat: Boolean = true,
    val allowBarre: Boolean = true,
    val maxFret: Int = 12,
    val sourceProgressionId: String = "",
    val useProgressionRhythm: Boolean = false,
    val songId: String = "",
    val songSectionId: String = "",
    val songTransitionFrom: String = "",
    val songTransitionTo: String = "",
)

data class PracticeSummaryUi(
    val todaySeconds: Long = 0,
    val sevenDaySessions: Int = 0,
    val sevenDaySeconds: Long = 0,
    val sevenDayCompletions: Int = 0,
    val mostPracticedChord: String = "",
    val bestCompletionCount: Int = 0,
    val bestStreak: Int = 0,
    val totalSessions: Int = 0,
    val sevenDayAttempts: Int = 0,
    val sevenDaySuccesses: Int = 0,
    val sevenDayFailures: Int = 0,
    val sevenDaySuccessRate: Double? = null,
    val strongestTransition: TransitionMasteryUi? = null,
    val weakestTransition: TransitionMasteryUi? = null,
    val highestStableBpm: Int? = null,
    val dailyPracticeSeconds: List<Long> = List(7) { 0L },
    val learningStreakDays: Int = 0,
)

data class PracticeHomeData(
    val summary: PracticeSummaryUi,
    val quickConfig: PracticeConfigUi,
)

data class PracticeResultUi(
    val sessionId: String,
    val actualSeconds: Int,
    val attemptCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val bestStreak: Int,
    val symbols: List<String>,
    val previousSuccessRate: Double?,
    val hardestTransition: String?,
    val difficultySuggestion: DifficultySuggestionUi,
) {
    val successRate: Double? get() = if (attemptCount == 0) null else successCount.toDouble() / attemptCount
}

data class AttemptProgressUi(
    val attemptCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
)

interface PracticeGateway {
    suspend fun home(): PracticeHomeData
    suspend fun summary(): PracticeSummaryUi
    suspend fun dailyPlan(
        profile: LearningProfile,
        favorites: Set<String>,
        availableChords: List<ChordUiModel>,
    ): DailyPracticePlan
    suspend fun prepare(config: PracticeConfigUi): ProgressionUiModel
    suspend fun savePreferences(config: PracticeConfigUi)
    suspend fun sessionProgress(sessionId: String): AttemptProgressUi
    suspend fun recordAttempt(
        sessionId: String,
        config: PracticeConfigUi,
        fromChord: String,
        toChord: String,
        fromVoicingId: String?,
        toVoicingId: String?,
        success: Boolean,
        confirmationOffsetMillis: Long?,
        stepToken: String,
    ): AttemptProgressUi
    suspend fun discardSession(sessionId: String)
    suspend fun saveResult(
        sessionId: String,
        startedAtEpochMillis: Long,
        config: PracticeConfigUi,
        actualSeconds: Int,
    ): PracticeResultUi
}

class DefaultPracticeGateway(
    private val recordStore: PracticeRecordStore,
    private val attemptStore: TransitionAttemptStore,
    private val preferenceStore: PracticePreferencesStore,
    private val practicePlanDraftStore: com.k2.music.PracticePlanDraftStore,
    private val progressionGateway: ProgressionGateway,
    private val lastConfigStore: LastPracticeConfigStore? = null,
    private val songGateway: SongGateway? = null,
    private val dailyPlanner: DailyPracticePlanner = DefaultDailyPracticePlanner(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PracticeGateway {
    override suspend fun summary(): PracticeSummaryUi = withContext(dispatcher) {
        buildSummary()
    }

    override suspend fun dailyPlan(
        profile: LearningProfile,
        favorites: Set<String>,
        availableChords: List<ChordUiModel>,
    ): DailyPracticePlan = withContext(dispatcher) {
        dailyPlanner.createPlan(
            profile,
            attemptStore.list(),
            recordStore.list(),
            preferenceStore.load().familiarVoicingIds,
            favorites,
            availableChords,
            System.currentTimeMillis(),
        )
    }

    override suspend fun home(): PracticeHomeData = withContext(dispatcher) {
        val preferences = preferenceStore.load()
        val last = recordStore.list().firstOrNull()
        val lastConfig = lastConfigStore?.load()
        val aiDraft = practicePlanDraftStore.consume()
        val quick = when {
            aiDraft != null -> PracticeConfigUi(
                mode = if (aiDraft.chordSymbols.size == 2) PracticeModeUi.TWO_CHORD else PracticeModeUi.MULTI_CHORD,
                symbols = aiDraft.chordSymbols.joinToString(" "),
                durationSeconds = aiDraft.durationSeconds,
                bpm = aiDraft.bpm,
                timeSignature = preferences.defaultTimeSignature.toString(),
                switchMode = PracticeSwitchUi.EACH_MEASURE,
                accentFirstBeat = preferences.accentFirstBeat,
                allowBarre = preferences.allowBarre,
                maxFret = preferences.maxFret,
            )
            lastConfig != null -> PracticeConfigUi(
                mode = lastConfig.mode.toUi(),
                symbols = lastConfig.chordSymbols.joinToString(" "),
                durationSeconds = lastConfig.durationSeconds,
                bpm = lastConfig.bpm,
                timeSignature = lastConfig.timeSignature,
                switchMode = lastConfig.switchMode.toUi(),
                accentFirstBeat = lastConfig.accentFirstBeat,
                allowBarre = lastConfig.allowBarre,
                maxFret = lastConfig.maxFret,
                sourceProgressionId = lastConfig.sourceProgressionId,
                useProgressionRhythm = lastConfig.useProgressionRhythm,
            )
            last != null -> PracticeConfigUi(
                mode = last.type.toUi(),
                symbols = last.chordSymbols.joinToString(" "),
                durationSeconds = last.plannedDurationSeconds.coerceIn(30, 3600),
                bpm = last.bpm,
                timeSignature = last.timeSignature,
                switchMode = when (last.switchMode) {
                    PracticeSession.SwitchMode.EACH_BEAT -> PracticeSwitchUi.EACH_BEAT
                    PracticeSession.SwitchMode.EACH_MEASURE -> PracticeSwitchUi.EACH_MEASURE
                },
                accentFirstBeat = preferences.accentFirstBeat,
                allowBarre = preferences.allowBarre,
                maxFret = preferences.maxFret,
                sourceProgressionId = last.sourceProgressionId,
                useProgressionRhythm = last.useProgressionRhythm,
            )
            else -> PracticeConfigUi(
                bpm = preferences.defaultBpm,
                timeSignature = preferences.defaultTimeSignature.toString(),
                accentFirstBeat = preferences.accentFirstBeat,
                allowBarre = preferences.allowBarre,
                maxFret = preferences.maxFret,
            )
        }
        PracticeHomeData(buildSummary(), quick)
    }

    override suspend fun prepare(config: PracticeConfigUi): ProgressionUiModel = withContext(dispatcher) {
        if (config.songId.isNotBlank()) {
            val songs = requireNotNull(songGateway) { "曲谱练习服务尚未初始化。" }
            val onlyTransition = if (config.songTransitionFrom.isNotBlank() && config.songTransitionTo.isNotBlank()) {
                SongTransition(config.songTransitionFrom, config.songTransitionTo)
            } else {
                null
            }
            val prepared = songs.preparePractice(
                config.songId,
                config.songSectionId.ifBlank { null },
                onlyTransition,
            ).progression
            return@withContext prepared.copy(
                bpm = config.bpm.coerceIn(40, 240),
                timeSignature = prepared.timeSignature,
                loop = true,
            )
        }
        val symbols = config.symbols.trim()
        val sourceId = config.sourceProgressionId.trim()
        val base = if (sourceId.isNotEmpty()) {
            progressionGateway.loadEditor(sourceId)
                ?: throw IllegalArgumentException("保存的和弦进行已不存在，请重新选择。")
        } else {
            progressionGateway.createDraft(symbols, "练习 · ${config.mode.label}")
        }
        val required = when (config.mode) {
            PracticeModeUi.TWO_CHORD -> 2
            PracticeModeUi.MULTI_CHORD, PracticeModeUi.RANDOM -> 2
        }
        require(base.steps.size >= required) { "${config.mode.label}至少需要 $required 个有效和弦。" }
        if (config.mode == PracticeModeUi.TWO_CHORD) {
            require(base.steps.size == 2) { "双和弦模式需要且只能填写两个和弦。" }
        }
        val ordered = if (config.mode == PracticeModeUi.RANDOM) {
            // A fixed shuffled cycle keeps playback deterministic while still changing the challenge order.
            base.steps.shuffled(kotlin.random.Random(base.id.hashCode()))
        } else {
            base.steps
        }
        val beats = when (config.switchMode) {
            PracticeSwitchUi.EACH_BEAT -> 1.0
            PracticeSwitchUi.EACH_MEASURE ->
                TimeSignature.parse(config.timeSignature).numerator.toDouble()
        }
        val prepared = base.copy(
            id = "practice-${UUID.randomUUID()}",
            name = "练习 · ${config.mode.label}",
            bpm = config.bpm.coerceIn(40, 240),
            timeSignature = TimeSignature.parse(config.timeSignature).toString(),
            loop = true,
            steps = ordered.mapIndexed { index, step ->
                step.copy(
                    order = index,
                    beats = if (config.useProgressionRhythm && sourceId.isNotEmpty()) step.beats else beats,
                )
            },
            saved = false,
            allowBarre = config.allowBarre,
            maxFret = config.maxFret,
        )
        runCatching { progressionGateway.recommend(prepared) }.getOrDefault(prepared)
    }

    override suspend fun savePreferences(config: PracticeConfigUi) = withContext(dispatcher) {
        val previous = preferenceStore.load()
        preferenceStore.save(
            PracticePreferences(
                previous.proficiency,
                config.allowBarre,
                config.maxFret.coerceIn(1, 24),
                config.bpm.coerceIn(40, 240),
                TimeSignature.parse(config.timeSignature),
                previous.defaultPlaybackMode,
                config.accentFirstBeat,
                previous.familiarVoicingIds,
            ),
        )
        val symbols = progressionGateway.createDraft(config.symbols).steps.map { it.chordSymbol }
        lastConfigStore?.save(
            LastPracticeConfig(
                config.mode.toCore(),
                symbols,
                config.durationSeconds.coerceIn(5, 86_400),
                config.bpm.coerceIn(40, 240),
                TimeSignature.parse(config.timeSignature).toString(),
                config.switchMode.toCore(),
                config.accentFirstBeat,
                config.allowBarre,
                config.maxFret.coerceIn(1, 24),
                config.sourceProgressionId,
                config.useProgressionRhythm,
            ),
        )
        Unit
    }

    override suspend fun sessionProgress(sessionId: String): AttemptProgressUi = withContext(dispatcher) {
        attemptStore.forSession(sessionId).toProgress()
    }

    override suspend fun recordAttempt(
        sessionId: String,
        config: PracticeConfigUi,
        fromChord: String,
        toChord: String,
        fromVoicingId: String?,
        toVoicingId: String?,
        success: Boolean,
        confirmationOffsetMillis: Long?,
        stepToken: String,
    ): AttemptProgressUi = withContext(dispatcher) {
        val normalizedToken = stepToken.trim()
        require(normalizedToken.isNotEmpty() && normalizedToken.length <= 512) { "播放步骤标识无效。" }
        val attemptId = UUID.nameUUIDFromBytes(
            "practice-step-v1|$sessionId|$normalizedToken".toByteArray(StandardCharsets.UTF_8),
        ).toString()
        if (attemptStore.read(attemptId) != null) {
            return@withContext attemptStore.forSession(sessionId).toProgress()
        }
        val attempt = TransitionAttempt(
            attemptId,
            sessionId,
            System.currentTimeMillis(),
            fromChord,
            toChord,
            fromVoicingId,
            toVoicingId,
            config.bpm.coerceIn(40, 240),
            TimeSignature.parse(config.timeSignature).toString(),
            config.switchMode.toCore(),
            success,
            confirmationOffsetMillis,
            config.mode.toCore(),
            config.songId,
            config.songSectionId,
        )
        attemptStore.save(attempt)
        attemptStore.forSession(sessionId).toProgress()
    }

    override suspend fun discardSession(sessionId: String) = withContext(dispatcher) {
        attemptStore.deleteSession(sessionId)
        Unit
    }

    override suspend fun saveResult(
        sessionId: String,
        startedAtEpochMillis: Long,
        config: PracticeConfigUi,
        actualSeconds: Int,
    ): PracticeResultUi = withContext(dispatcher) {
        val symbols = progressionGateway.createDraft(config.symbols).steps.map { it.chordSymbol }
        val attempts = attemptStore.forSession(sessionId)
        val progress = attempts.toProgress()
        val previous = recordStore.list().firstOrNull {
            !it.legacy && it.id != sessionId && it.type == config.mode.toCore() && it.chordSymbols == symbols
        }
        val endedAt = System.currentTimeMillis().coerceAtLeast(startedAtEpochMillis)
        val session = PracticeSession.recorded(
            sessionId,
            startedAtEpochMillis,
            endedAt,
            config.mode.toCore(),
            symbols,
            config.bpm.coerceIn(40, 240),
            TimeSignature.parse(config.timeSignature).toString(),
            config.switchMode.toCore(),
            config.durationSeconds.coerceIn(5, 86_400),
            actualSeconds.coerceIn(0, 86_400),
            progress.attemptCount,
            progress.successCount,
            progress.failureCount,
            progress.bestStreak,
            config.sourceProgressionId,
            config.useProgressionRhythm,
        )
        recordStore.save(session)
        if (config.songId.isNotBlank()) {
            requireNotNull(songGateway) { "曲谱练习服务尚未初始化。" }.savePracticeRun(
                songId = config.songId,
                sectionId = config.songSectionId.ifBlank { null },
                mode = SongPracticeMode.GUIDED_TRANSITION,
                bpm = session.bpm,
                startedAt = session.startedAtEpochMillis,
                endedAt = session.endedAtEpochMillis,
                actualDurationSeconds = session.actualDurationSeconds,
                completed = session.actualDurationSeconds >= session.plannedDurationSeconds,
                difficultTransitions = emptyList(),
                runId = sessionId,
            )
        }
        val comparable = (listOf(session) + recordStore.list().filter {
            !it.legacy && it.id != sessionId && it.type == session.type && it.chordSymbols == symbols
        }).take(2)
        val stableComparable = comparable.takeWhile {
            it.attemptCount >= 10 && it.successCount.toDouble() / it.attemptCount >= 0.9
        }.size
        val suggestion = suggestPracticeDifficulty(attempts, session.bpm, stableComparable)
        val hardest = attempts
            .groupBy { it.fromChord to it.toChord }
            .mapValues { (_, values) -> values.count { it.success }.toDouble() / values.size }
            .minWithOrNull(compareBy<Map.Entry<Pair<String, String>, Double>> { it.value }
                .thenByDescending { attempts.count { attempt -> attempt.fromChord == it.key.first && attempt.toChord == it.key.second } }
                .thenBy { it.key.first }
                .thenBy { it.key.second })
            ?.key
        PracticeResultUi(
            session.id,
            session.actualDurationSeconds,
            session.attemptCount,
            session.successCount,
            session.failureCount,
            session.bestStreak,
            session.chordSymbols,
            previous?.let { if (it.attemptCount == 0) null else it.successCount.toDouble() / it.attemptCount },
            hardest?.let { "${it.first} → ${it.second}" },
            suggestion,
        )
    }

    private fun List<TransitionAttempt>.toProgress(): AttemptProgressUi {
        var currentStreak = 0
        var bestStreak = 0
        var successCount = 0
        for (attempt in this.sortedWith(compareBy<TransitionAttempt> { it.timestampEpochMillis }.thenBy { it.id })) {
            if (attempt.success) {
                successCount++
                currentStreak++
                bestStreak = maxOf(bestStreak, currentStreak)
            } else {
                currentStreak = 0
            }
        }
        return AttemptProgressUi(size, successCount, size - successCount, currentStreak, bestStreak)
    }

    private fun PracticeSummary.toUi() = PracticeSummaryUi(
        todayPracticeSeconds,
        lastSevenDaysSessionCount,
        lastSevenDaysPracticeSeconds,
        lastSevenDaysCompletionCount,
        mostPracticedChord,
        bestCompletionCount,
        bestStreak,
        totalSessionCount,
    )

    private fun buildSummary(): PracticeSummaryUi {
        val now = System.currentTimeMillis()
        val sessions = recordStore.list()
        val sessionIds = sessions.mapTo(hashSetOf()) { it.id }
        val attempts = attemptStore.list().filter { it.sessionId in sessionIds }
        val base = recordStore.summarizeNow().toUi()
        val calendar = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val todayStart = calendar.timeInMillis
        val sevenDayStart = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -6) }.timeInMillis
        val recentAttempts = attempts.filter { it.timestampEpochMillis in sevenDayStart..now }
        val recentSuccesses = recentAttempts.count { it.success }
        val masteries = calculateTransitionMasteries(attempts, now)
        val scored = masteries.filter { it.score != null }
        val strongest = scored.maxWithOrNull(
            compareBy<TransitionMasteryUi> { it.score }.thenByDescending { it.key.label },
        )
        val weakest = scored.minWithOrNull(
            compareBy<TransitionMasteryUi> { it.score }.thenBy { it.key.label },
        )
        val daily = (6 downTo 0).map { daysAgo ->
            val start = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }.timeInMillis
            val end = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -daysAgo + 1) }.timeInMillis
            sessions.filter { it.startedAtEpochMillis in start until end }.sumOf { it.actualDurationSeconds.toLong() }
        }
        val practicedDays = sessions.mapTo(hashSetOf()) { session ->
            Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = session.startedAtEpochMillis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }
        var streak = 0
        var cursor = todayStart
        if (cursor !in practicedDays) cursor = (calendar.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }.timeInMillis
        while (cursor in practicedDays) {
            streak++
            val previous = Calendar.getInstance(TimeZone.getDefault()).apply {
                timeInMillis = cursor
                add(Calendar.DAY_OF_YEAR, -1)
            }
            cursor = previous.timeInMillis
        }
        return base.copy(
            sevenDayCompletions = recentAttempts.size,
            sevenDayAttempts = recentAttempts.size,
            sevenDaySuccesses = recentSuccesses,
            sevenDayFailures = recentAttempts.size - recentSuccesses,
            sevenDaySuccessRate = recentSuccesses.toDouble().div(recentAttempts.size).takeIf { recentAttempts.isNotEmpty() },
            strongestTransition = strongest,
            weakestTransition = weakest,
            highestStableBpm = masteries.mapNotNull { it.highestStableBpm }.maxOrNull(),
            dailyPracticeSeconds = daily,
            learningStreakDays = streak,
        )
    }

    private fun PracticeSession.Type.toUi() = when (this) {
        PracticeSession.Type.TWO_CHORD_TRANSITION -> PracticeModeUi.TWO_CHORD
        PracticeSession.Type.PROGRESSION_LOOP -> PracticeModeUi.MULTI_CHORD
        PracticeSession.Type.RANDOM_CHALLENGE -> PracticeModeUi.RANDOM
    }

    private fun PracticeSession.SwitchMode.toUi() = when (this) {
        PracticeSession.SwitchMode.EACH_BEAT -> PracticeSwitchUi.EACH_BEAT
        PracticeSession.SwitchMode.EACH_MEASURE -> PracticeSwitchUi.EACH_MEASURE
    }

    private fun PracticeModeUi.toCore() = when (this) {
        PracticeModeUi.TWO_CHORD -> PracticeSession.Type.TWO_CHORD_TRANSITION
        PracticeModeUi.MULTI_CHORD -> PracticeSession.Type.PROGRESSION_LOOP
        PracticeModeUi.RANDOM -> PracticeSession.Type.RANDOM_CHALLENGE
    }

    private fun PracticeSwitchUi.toCore() = when (this) {
        PracticeSwitchUi.EACH_BEAT -> PracticeSession.SwitchMode.EACH_BEAT
        PracticeSwitchUi.EACH_MEASURE -> PracticeSession.SwitchMode.EACH_MEASURE
    }
}

package com.k2.music.ui.gateway

import com.k2.music.PracticePreferences
import com.k2.music.PracticePreferencesStore
import com.k2.music.PracticeRecordStore
import com.k2.music.PracticeSession
import com.k2.music.PracticeSummary
import com.k2.music.TimeSignature
import com.k2.music.ui.model.ProgressionUiModel
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class PracticeModeUi(val label: String) {
    TWO_CHORD("双和弦"),
    MULTI_CHORD("多和弦"),
    RANDOM("随机挑战"),
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
)

data class PracticeHomeData(
    val summary: PracticeSummaryUi,
    val quickConfig: PracticeConfigUi,
)

data class PracticeResultUi(
    val sessionId: String,
    val actualSeconds: Int,
    val completionCount: Int,
    val bestStreak: Int,
    val symbols: List<String>,
    val previousCompletionCount: Int?,
)

interface PracticeGateway {
    suspend fun home(): PracticeHomeData
    suspend fun summary(): PracticeSummaryUi
    suspend fun prepare(config: PracticeConfigUi): ProgressionUiModel
    suspend fun savePreferences(config: PracticeConfigUi)
    suspend fun saveResult(
        config: PracticeConfigUi,
        actualSeconds: Int,
        completionCount: Int,
        bestStreak: Int,
    ): PracticeResultUi
}

class DefaultPracticeGateway(
    private val recordStore: PracticeRecordStore,
    private val preferenceStore: PracticePreferencesStore,
    private val practicePlanDraftStore: com.k2.music.PracticePlanDraftStore,
    private val progressionGateway: ProgressionGateway,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PracticeGateway {
    override suspend fun summary(): PracticeSummaryUi = withContext(dispatcher) {
        recordStore.summarizeNow().toUi()
    }

    override suspend fun home(): PracticeHomeData = withContext(dispatcher) {
        val preferences = preferenceStore.load()
        val last = recordStore.list().firstOrNull()
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
            last != null -> PracticeConfigUi(
                mode = last.type.toUi(),
                symbols = last.chordSymbols.joinToString(" "),
                durationSeconds = last.durationSeconds.coerceIn(30, 3600),
                bpm = last.bpm,
                timeSignature = preferences.defaultTimeSignature.toString(),
                switchMode = PracticeSwitchUi.EACH_MEASURE,
                accentFirstBeat = preferences.accentFirstBeat,
                allowBarre = preferences.allowBarre,
                maxFret = preferences.maxFret,
            )
            else -> PracticeConfigUi(
                bpm = preferences.defaultBpm,
                timeSignature = preferences.defaultTimeSignature.toString(),
                accentFirstBeat = preferences.accentFirstBeat,
                allowBarre = preferences.allowBarre,
                maxFret = preferences.maxFret,
            )
        }
        PracticeHomeData(recordStore.summarizeNow().toUi(), quick)
    }

    override suspend fun prepare(config: PracticeConfigUi): ProgressionUiModel = withContext(dispatcher) {
        val symbols = config.symbols.trim()
        val base = progressionGateway.createDraft(symbols, "练习 · ${config.mode.label}")
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
            steps = ordered.mapIndexed { index, step -> step.copy(order = index, beats = beats) },
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
        Unit
    }

    override suspend fun saveResult(
        config: PracticeConfigUi,
        actualSeconds: Int,
        completionCount: Int,
        bestStreak: Int,
    ): PracticeResultUi = withContext(dispatcher) {
        val previous = recordStore.list().firstOrNull()
        val symbols = progressionGateway.createDraft(config.symbols).steps.map { it.chordSymbol }
        val session = PracticeSession.completed(
            System.currentTimeMillis(),
            config.mode.toCore(),
            symbols,
            config.bpm.coerceIn(40, 240),
            actualSeconds.coerceIn(0, 86_400),
            completionCount.coerceAtLeast(0),
            bestStreak.coerceIn(0, completionCount.coerceAtLeast(0)),
        )
        recordStore.add(session)
        PracticeResultUi(
            session.id,
            session.durationSeconds,
            session.completionCount,
            session.bestStreak,
            session.chordSymbols,
            previous?.completionCount,
        )
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

    private fun PracticeSession.Type.toUi() = when (this) {
        PracticeSession.Type.TWO_CHORD_TRANSITION -> PracticeModeUi.TWO_CHORD
        PracticeSession.Type.PROGRESSION_LOOP -> PracticeModeUi.MULTI_CHORD
        PracticeSession.Type.RANDOM_CHALLENGE -> PracticeModeUi.RANDOM
    }

    private fun PracticeModeUi.toCore() = when (this) {
        PracticeModeUi.TWO_CHORD -> PracticeSession.Type.TWO_CHORD_TRANSITION
        PracticeModeUi.MULTI_CHORD -> PracticeSession.Type.PROGRESSION_LOOP
        PracticeModeUi.RANDOM -> PracticeSession.Type.RANDOM_CHALLENGE
    }
}

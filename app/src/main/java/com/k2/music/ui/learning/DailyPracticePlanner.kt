package com.k2.music.ui.learning

import com.k2.music.PracticeSession
import com.k2.music.TransitionAttempt
import com.k2.music.ui.gateway.PracticeConfigUi
import com.k2.music.ui.gateway.PracticeModeUi
import com.k2.music.ui.gateway.PracticeSwitchUi
import com.k2.music.ui.gateway.calculateTransitionMasteries
import com.k2.music.ui.model.ChordUiModel

enum class DailyTaskType {
    CONTINUE_LAST,
    REVIEW_WEAK_TRANSITION,
    REVIEW_STALE_CHORD,
    LEARN_NEW_CHORD,
    PRACTICE_PROGRESSION,
}

data class DailyPracticeTask(
    val type: DailyTaskType,
    val title: String,
    val reason: String,
    val config: PracticeConfigUi?,
    val chordSymbol: String? = null,
)

data class DailyPracticePlan(
    val generatedAt: Long,
    val targetMinutes: Int,
    val tasks: List<DailyPracticeTask>,
    val reasons: List<String>,
) {
    val weakestTransition: DailyPracticeTask?
        get() = tasks.firstOrNull { it.type == DailyTaskType.REVIEW_WEAK_TRANSITION }
    val newContent: DailyPracticeTask?
        get() = tasks.firstOrNull { it.type == DailyTaskType.LEARN_NEW_CHORD }
}

fun interface DailyPracticePlanner {
    fun createPlan(
        profile: LearningProfile,
        recentAttempts: List<TransitionAttempt>,
        recentSessions: List<PracticeSession>,
        familiarVoicings: Set<String>,
        favorites: Set<String>,
        availableChords: List<ChordUiModel>,
        nowEpochMillis: Long,
    ): DailyPracticePlan
}

class DefaultDailyPracticePlanner : DailyPracticePlanner {
    override fun createPlan(
        profile: LearningProfile,
        recentAttempts: List<TransitionAttempt>,
        recentSessions: List<PracticeSession>,
        familiarVoicings: Set<String>,
        favorites: Set<String>,
        availableChords: List<ChordUiModel>,
        nowEpochMillis: Long,
    ): DailyPracticePlan {
        val available = availableChords.filter { chord -> allowedChord(chord, profile) }
        val availableSymbols = available.mapTo(linkedSetOf()) { it.symbol }
        val validSessionIds = recentSessions.mapTo(hashSetOf()) { it.id }
        val trustedAttempts = recentAttempts.filter { it.sessionId in validSessionIds }
        val last = recentSessions.firstOrNull()
        val tasks = mutableListOf<DailyPracticeTask>()

        last?.let { session ->
            tasks += DailyPracticeTask(
                DailyTaskType.CONTINUE_LAST,
                "继续上次练习",
                if (session.legacy) "恢复上次配置；旧版完成记录不参与成功率" else {
                    val rate = if (session.attemptCount == 0) null else session.successCount * 100.0 / session.attemptCount
                    rate?.let { "上次成功率 ${"%.0f".format(it)}%" } ?: "上次尚未记录结果"
                },
                session.toConfig(profile),
            )
        }

        val masteries = calculateTransitionMasteries(trustedAttempts, nowEpochMillis)
        val weakest = masteries.filter { it.score != null }.minWithOrNull(
            compareBy<com.k2.music.ui.gateway.TransitionMasteryUi> { it.score }
                .thenBy { it.key.fromChord }
                .thenBy { it.key.toChord },
        )
        weakest?.takeIf { it.key.fromChord in availableSymbols && it.key.toChord in availableSymbols }?.let { mastery ->
            val pairAttempts = trustedAttempts.filter {
                it.fromChord == mastery.key.fromChord && it.toChord == mastery.key.toChord
            }
            val suggestedBpm = (pairAttempts.maxByOrNull { it.timestampEpochMillis }?.bpm ?: 50).let { bpm ->
                if ((mastery.successRate ?: 1.0) < 0.75) bpm - 5 else bpm
            }.coerceIn(40, 240)
            tasks += DailyPracticeTask(
                DailyTaskType.REVIEW_WEAK_TRANSITION,
                "复习 ${mastery.key.label}",
                "${mastery.reason}，建议从 $suggestedBpm BPM 开始",
                PracticeConfigUi(
                    mode = PracticeModeUi.TWO_CHORD,
                    symbols = "${mastery.key.fromChord} ${mastery.key.toChord}",
                    durationSeconds = (profile.dailyTargetMinutes * 60).coerceIn(60, 180),
                    bpm = suggestedBpm,
                    switchMode = PracticeSwitchUi.EACH_MEASURE,
                    allowBarre = profile.preferredExperienceMode == com.k2.music.ui.preferences.ExperienceMode.PROFESSIONAL,
                    maxFret = if (profile.preferredExperienceMode == com.k2.music.ui.preferences.ExperienceMode.BEGINNER) 5 else 24,
                ),
            )
        }

        val practicedAt = mutableMapOf<String, Long>()
        recentSessions.forEach { session ->
            session.chordSymbols.forEach { symbol ->
                practicedAt[symbol] = maxOf(practicedAt[symbol] ?: 0L, session.startedAtEpochMillis)
            }
        }
        val stale = (favorites + practicedAt.keys)
            .filter { it in availableSymbols }
            .minWithOrNull(compareBy<String> { practicedAt[it] ?: Long.MIN_VALUE }.thenBy { it })
        stale?.takeIf { symbol -> nowEpochMillis - (practicedAt[symbol] ?: 0L) >= 14L * DAY_MILLIS }?.let { symbol ->
            val partner = beginnerSequence.firstOrNull { it != symbol && it in availableSymbols } ?: return@let
            tasks += DailyPracticeTask(
                DailyTaskType.REVIEW_STALE_CHORD,
                "复习久未练习的 $symbol",
                "最近两周没有在已保存会话中练到这个和弦",
                PracticeConfigUi(
                    symbols = "$symbol $partner",
                    durationSeconds = 60,
                    bpm = if (profile.skillLevel == SkillLevel.BEGINNER) 50 else 60,
                    allowBarre = profile.preferredExperienceMode == com.k2.music.ui.preferences.ExperienceMode.PROFESSIONAL,
                    maxFret = if (profile.preferredExperienceMode == com.k2.music.ui.preferences.ExperienceMode.BEGINNER) 5 else 24,
                ),
                chordSymbol = symbol,
            )
        }

        val practicedSymbols = practicedAt.keys
        val newSymbol = beginnerSequence
            .firstOrNull { it in availableSymbols && it !in practicedSymbols && familiarVoicings.none { id -> id.startsWith("$it|") } }
            ?: available.asSequence().map { it.symbol }.firstOrNull { it !in practicedSymbols }
        newSymbol?.let { symbol ->
            tasks += DailyPracticeTask(
                DailyTaskType.LEARN_NEW_CHORD,
                "学习 $symbol",
                if (profile.skillLevel == SkillLevel.BEGINNER) "常见于基础歌曲，是建议优先掌握的开放和弦" else "当前资料中练习较少，适合作为下一项内容",
                null,
                chordSymbol = symbol,
            )
        }

        if (last == null && "C" in availableSymbols && "G" in availableSymbols) {
            tasks.add(
                0,
                DailyPracticeTask(
                    DailyTaskType.PRACTICE_PROGRESSION,
                    "第一次换和弦：C 与 G",
                    "从两个常见开放和弦开始，每小节切换",
                    PracticeConfigUi(
                        symbols = "C G",
                        durationSeconds = 60,
                        bpm = 50,
                        allowBarre = false,
                        maxFret = 5,
                    ),
                ),
            )
        }
        return DailyPracticePlan(
            generatedAt = nowEpochMillis,
            targetMinutes = profile.dailyTargetMinutes,
            tasks = tasks.distinctBy { it.type }.take(5),
            reasons = tasks.map { it.reason }.distinct(),
        )
    }

    private fun allowedChord(chord: ChordUiModel, profile: LearningProfile): Boolean {
        if (chord.voicings.isEmpty()) return false
        if (profile.preferredExperienceMode == com.k2.music.ui.preferences.ExperienceMode.PROFESSIONAL) return true
        return chord.voicings.any { voicing -> !voicing.barre && (voicing.frets.maxOrNull() ?: 0) <= 5 }
    }

    private fun PracticeSession.toConfig(profile: LearningProfile) = PracticeConfigUi(
        mode = when (type) {
            PracticeSession.Type.TWO_CHORD_TRANSITION -> PracticeModeUi.TWO_CHORD
            PracticeSession.Type.PROGRESSION_LOOP -> PracticeModeUi.MULTI_CHORD
            PracticeSession.Type.RANDOM_CHALLENGE -> PracticeModeUi.RANDOM
        },
        symbols = chordSymbols.joinToString(" "),
        durationSeconds = plannedDurationSeconds.coerceIn(30, 3_600),
        bpm = bpm,
        timeSignature = timeSignature,
        switchMode = when (switchMode) {
            PracticeSession.SwitchMode.EACH_BEAT -> PracticeSwitchUi.EACH_BEAT
            PracticeSession.SwitchMode.EACH_MEASURE -> PracticeSwitchUi.EACH_MEASURE
        },
        allowBarre = profile.preferredExperienceMode == com.k2.music.ui.preferences.ExperienceMode.PROFESSIONAL,
        maxFret = if (profile.preferredExperienceMode == com.k2.music.ui.preferences.ExperienceMode.BEGINNER) 5 else 24,
    )

    private companion object {
        val beginnerSequence = listOf("C", "G", "Am", "Em", "D", "A", "Dm", "E")
        const val DAY_MILLIS = 86_400_000L
    }
}

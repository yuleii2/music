package com.k2.music.ui.gateway

import com.k2.music.TransitionAttempt
import kotlin.math.roundToInt

data class TransitionKeyUi(val fromChord: String, val toChord: String) {
    val label: String get() = "$fromChord → $toChord"
}

enum class TransitionMasteryLevel(val label: String) {
    NEEDS_PRACTICE("待练习"),
    BUILDING("正在建立"),
    STABLE("基本稳定"),
    PROFICIENT("熟练"),
}

data class TransitionMasteryUi(
    val key: TransitionKeyUi,
    val sampleCount: Int,
    val successRate: Double?,
    val score: Int?,
    val level: TransitionMasteryLevel?,
    val highestStableBpm: Int?,
    val currentStreak: Int,
    val lastPracticedAt: Long?,
    val reason: String,
)

enum class DifficultyAction { INCREASE_5, KEEP, DECREASE_5, DECREASE_10, NEED_MORE_DATA }

data class DifficultySuggestionUi(
    val action: DifficultyAction,
    val suggestedBpm: Int,
    val reason: String,
)

/** Directional, deterministic and AI-independent transition mastery. */
fun calculateTransitionMasteries(
    attempts: List<TransitionAttempt>,
    nowEpochMillis: Long,
    minimumSamples: Int = 5,
): List<TransitionMasteryUi> = attempts
    .groupBy { TransitionKeyUi(it.fromChord, it.toChord) }
    .map { (key, all) ->
        val recent = all.sortedWith(
            compareByDescending<TransitionAttempt> { it.timestampEpochMillis }.thenBy { it.id },
        ).take(20)
        val successRate = recent.count { it.success }.toDouble() / recent.size
        val chronological = recent.sortedWith(compareBy<TransitionAttempt> { it.timestampEpochMillis }.thenBy { it.id })
        var streak = 0
        chronological.forEach { attempt -> streak = if (attempt.success) streak + 1 else 0 }
        val stableBpm = recent
            .groupBy { it.bpm }
            .filterValues { values -> values.size >= 5 && values.count { it.success }.toDouble() / values.size >= 0.8 }
            .keys
            .maxOrNull()
        val lastPracticed = recent.maxOfOrNull { it.timestampEpochMillis }
        val enough = recent.size >= minimumSamples
        val recencyScore = when {
            lastPracticed == null -> 0.0
            nowEpochMillis - lastPracticed <= 7L * DAY_MILLIS -> 10.0
            nowEpochMillis - lastPracticed <= 30L * DAY_MILLIS -> 5.0
            else -> 0.0
        }
        val score = if (!enough) null else (
            successRate * 60.0 +
                ((stableBpm ?: 0).coerceAtMost(100) / 100.0) * 20.0 +
                (streak.coerceAtMost(5) / 5.0) * 10.0 +
                recencyScore
            ).roundToInt().coerceIn(0, 100)
        val level = score?.let {
            when {
                it < 35 -> TransitionMasteryLevel.NEEDS_PRACTICE
                it < 60 -> TransitionMasteryLevel.BUILDING
                it < 80 -> TransitionMasteryLevel.STABLE
                else -> TransitionMasteryLevel.PROFICIENT
            }
        }
        TransitionMasteryUi(
            key = key,
            sampleCount = recent.size,
            successRate = successRate.takeIf { enough },
            score = score,
            level = level,
            highestStableBpm = stableBpm,
            currentStreak = streak,
            lastPracticedAt = lastPracticed,
            reason = if (!enough) {
                "数据不足：至少需要 $minimumSamples 次结果，当前 ${recent.size} 次"
            } else {
                "最近 ${recent.size} 次成功率 ${"%.0f".format(successRate * 100)}%" +
                    (stableBpm?.let { "，在 $it BPM 达到稳定样本" } ?: "，尚无稳定速度样本")
            },
        )
    }
    .sortedWith(compareBy<TransitionMasteryUi> { it.key.fromChord }.thenBy { it.key.toChord })

/** V1.4 suggestion rules. A high-rate speed-up also requires two stable comparable sessions. */
fun suggestPracticeDifficulty(
    recentAttempts: List<TransitionAttempt>,
    currentBpm: Int,
    stableComparableSessions: Int,
): DifficultySuggestionUi {
    val recent = recentAttempts.sortedByDescending { it.timestampEpochMillis }.take(20)
    if (recent.size < 10) {
        return DifficultySuggestionUi(
            DifficultyAction.NEED_MORE_DATA,
            currentBpm,
            "完成更多尝试后生成速度建议（至少 10 次，当前 ${recent.size} 次）。",
        )
    }
    val rate = recent.count { it.success }.toDouble() / recent.size
    return when {
        rate >= 0.9 && stableComparableSessions >= 2 -> DifficultySuggestionUi(
            DifficultyAction.INCREASE_5,
            (currentBpm + 5).coerceAtMost(240),
            "最近成功率 ${"%.0f".format(rate * 100)}%，且连续两次练习稳定，建议提高 5 BPM。",
        )
        rate >= 0.75 -> DifficultySuggestionUi(
            DifficultyAction.KEEP,
            currentBpm,
            "最近成功率 ${"%.0f".format(rate * 100)}%，保持当前速度巩固稳定性。",
        )
        rate >= 0.5 -> DifficultySuggestionUi(
            DifficultyAction.DECREASE_5,
            (currentBpm - 5).coerceAtLeast(40),
            "最近成功率 ${"%.0f".format(rate * 100)}%，建议降低 5 BPM。",
        )
        else -> DifficultySuggestionUi(
            DifficultyAction.DECREASE_10,
            (currentBpm - 10).coerceAtLeast(40),
            "最近成功率 ${"%.0f".format(rate * 100)}%，建议降低 10 BPM 或改为每小节切换。",
        )
    }
}

private const val DAY_MILLIS = 86_400_000L

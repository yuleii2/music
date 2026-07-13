package com.k2.music.ui.model

import com.k2.music.ChordProgression
import com.k2.music.PracticePreferences
import com.k2.music.ProgressionStep
import com.k2.music.TimeSignature
import com.k2.music.VoicingRecommendationMode

enum class ProgressionPlaybackMode(val label: String) {
    WHOLE_CHORD("整和弦"),
    ARPEGGIO("分解和弦"),
}
data class ProgressionVoicingOptionUi(
    val id: String,
    val voicing: VoicingUiModel,
)

data class ProgressionStepUi(
    val chordSymbol: String,
    val voicingId: String,
    val beats: Double,
    val strumPattern: String,
    val order: Int,
    val chord: ChordUiModel?,
    val voicingOptions: List<ProgressionVoicingOptionUi>,
) {
    val selectedVoicing: VoicingUiModel?
        get() = voicingOptions.firstOrNull { it.id == voicingId }?.voicing
            ?: voicingOptions.firstOrNull()?.voicing
}

data class ProgressionUiModel(
    val id: String,
    val name: String,
    val keySignature: String,
    val timeSignature: String,
    val bpm: Int,
    val loop: Boolean,
    val steps: List<ProgressionStepUi>,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val notes: String,
    val saved: Boolean,
    val restoredDraft: Boolean = false,
    val playbackMode: ProgressionPlaybackMode = ProgressionPlaybackMode.WHOLE_CHORD,
    val recommendationMode: VoicingRecommendationMode = VoicingRecommendationMode.AUTO,
    val allowBarre: Boolean = true,
    val maxFret: Int = 12,
    val recommendationReasons: List<String> = emptyList(),
) {
    val symbols: String get() = steps.joinToString(" ") { it.chordSymbol }
}

data class ProgressionSummaryUi(
    val id: String,
    val name: String,
    val keySignature: String,
    val bpm: Int,
    val timeSignature: String,
    val stepCount: Int,
    val symbols: String,
    val updatedAtEpochMillis: Long,
)

data class ProgressionPresetUi(
    val id: String,
    val name: String,
    val keySignature: String,
    val symbols: List<String>,
    val beatsPerChord: Double,
)

internal fun ProgressionUiModel.toCore(): ChordProgression = ChordProgression(
    id,
    name.trim(),
    keySignature.trim(),
    TimeSignature.parse(timeSignature),
    bpm,
    loop,
    steps.mapIndexed { index, step ->
        ProgressionStep(
            step.chordSymbol,
            step.voicingId,
            step.beats,
            step.strumPattern,
            index,
        )
    },
    createdAtEpochMillis,
    updatedAtEpochMillis,
    notes,
)

internal fun ProgressionPlaybackMode.toCore(): PracticePreferences.PlaybackMode = when (this) {
    ProgressionPlaybackMode.WHOLE_CHORD -> PracticePreferences.PlaybackMode.WHOLE_CHORD
    ProgressionPlaybackMode.ARPEGGIO -> PracticePreferences.PlaybackMode.ARPEGGIO
}

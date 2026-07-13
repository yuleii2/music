package com.k2.music.ui.model

import com.k2.music.Chord
import com.k2.music.CustomVoicing
import com.k2.music.Voicing

data class VoicingUiModel(
    val id: String,
    val customId: String? = null,
    val name: String,
    val frets: List<Int>,
    val fingers: List<Int>,
    val startFret: Int,
    val displayFrets: Int,
    val difficulty: String,
    val recommended: Boolean,
    val simplified: Boolean,
    val barre: Boolean,
    val description: String,
    val stringNotes: List<String?>,
    val midiNotes: List<Int>,
) {
    val isCustom: Boolean get() = customId != null
    val isOpen: Boolean get() = frets.any { it == 0 }
    val playableMidiNotes: IntArray get() = midiNotes.filter { it > 0 }.toIntArray()
}

data class ChordUiModel(
    val symbol: String,
    val chineseName: String,
    val root: String,
    val qualityId: String,
    val quality: String,
    val bassNote: String,
    val intervals: List<String>,
    val notes: List<String>,
    val aliases: List<String>,
    val description: String,
    val voicings: List<VoicingUiModel>,
    val favorite: Boolean = false,
) {
    val hasVoicings: Boolean get() = voicings.isNotEmpty()
    val previewVoicing: VoicingUiModel? get() = voicings.firstOrNull()
    val difficultyLabel: String get() = previewVoicing?.difficulty ?: "理论"
}

data class ChordLookupUiResult(
    val recognized: Boolean,
    val chord: ChordUiModel? = null,
    val message: String? = null,
)

internal fun Chord.toUiModel(
    favorite: Boolean = false,
    customVoicings: List<CustomVoicing> = emptyList(),
): ChordUiModel {
    val builtIns = voicings.mapIndexed { index, voicing ->
        voicing.toUiModel("$symbol:built-in:$index")
    }
    val custom = customVoicings.map { entry -> entry.toUiModel(symbol) }
    return ChordUiModel(
        symbol = symbol,
        chineseName = chineseName,
        root = root,
        qualityId = qualityId,
        quality = quality,
        bassNote = bassNote,
        intervals = intervals.toList(),
        notes = notes.toList(),
        aliases = aliases.toList(),
        description = description,
        voicings = builtIns + custom,
        favorite = favorite,
    )
}

private fun Voicing.toUiModel(id: String, customId: String? = null): VoicingUiModel =
    VoicingUiModel(
        id = id,
        customId = customId,
        name = name,
        frets = frets.toList(),
        fingers = fingers.toList(),
        startFret = startFret,
        displayFrets = displayFrets,
        difficulty = difficulty,
        recommended = recommended,
        simplified = simplified,
        barre = barre,
        description = description,
        stringNotes = stringNotes.toList(),
        midiNotes = midiNotes.toList(),
    )

fun CustomVoicing.toUiModel(symbol: String = chordSymbol): VoicingUiModel =
    toVoicing().toUiModel(
        id = "$symbol:custom:$id",
        customId = id,
    )

fun fretboardDescription(chord: ChordUiModel, voicing: VoicingUiModel): String {
    val strings = voicing.frets.mapIndexed { index, fret ->
        val stringNumber = 6 - index
        val state = when (fret) {
            -1 -> "闷弦 X"
            0 -> "空弦 O"
            else -> "${fret} 品${voicing.fingers.getOrNull(index)?.takeIf { it > 0 }?.let { "，$it 指" } ?: ""}"
        }
        "$stringNumber 弦 $state"
    }.joinToString("；")
    val barre = if (voicing.barre) "包含横按" else "不含横按"
    return "${chord.symbol}，${voicing.name}，起始 ${voicing.startFret} 品，$strings；$barre。"
}

package com.k2.music.ui.model

import com.k2.music.Chord
import com.k2.music.CustomVoicing
import com.k2.music.NoteUtils
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
    val omittedIntervals: List<String> = emptyList(),
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
    val displayName: String = chineseName,
    val extensions: List<String> = emptyList(),
    val alterations: List<String> = emptyList(),
    val omissions: List<String> = emptyList(),
    val additions: List<String> = emptyList(),
    val requiredIntervals: List<String> = emptyList(),
    val optionalIntervals: List<String> = emptyList(),
    val omittableIntervals: List<String> = emptyList(),
    val requiredAnyOf: List<List<String>> = emptyList(),
    val pitchClasses: List<Int> = emptyList(),
) {
    val hasVoicings: Boolean get() = voicings.isNotEmpty()
    val previewVoicing: VoicingUiModel? get() = voicings.firstOrNull()
    val difficultyLabel: String get() = previewVoicing?.difficulty ?: "理论"
    val chordBodySymbol: String get() = symbol.substringBefore('/')
    val slashTypeLabel: String
        get() {
            if (bassNote.isBlank()) return "原位和弦"
            val bassPitch = runCatching { NoteUtils.semitone(bassNote) }.getOrNull()
            val index = notes.indexOfFirst { note ->
                bassPitch != null && runCatching { NoteUtils.semitone(note) }.getOrNull() == bassPitch
            }
            return when (index) {
                0 -> "根音低音斜杠和弦"
                1 -> "第一转位斜杠和弦"
                2 -> "第二转位斜杠和弦"
                in 3..Int.MAX_VALUE -> "扩展音低音斜杠和弦"
                else -> "独立低音斜杠和弦"
            }
        }
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
        displayName = displayName,
        extensions = extensions.toList(),
        alterations = alterations.toList(),
        omissions = omissions.toList(),
        additions = additions.toList(),
        requiredIntervals = requiredIntervals.toList(),
        optionalIntervals = optionalIntervals.toList(),
        omittableIntervals = omittableIntervals.toList(),
        requiredAnyOf = requiredAnyOf.map { it.toList() },
        pitchClasses = pitchClasses.toList(),
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
        omittedIntervals = omittedIntervals.toList(),
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
    val omissions = voicing.omittedIntervals.takeIf { it.isNotEmpty() }
        ?.joinToString("、", prefix = "；省略音程 ")
        .orEmpty()
    return "${chord.symbol}，${voicing.name}，起始 ${voicing.startFret} 品，$strings；$barre$omissions。"
}

package com.k2.music.ui.gateway

import com.k2.music.CapoAssistant
import com.k2.music.ChordIdentifier
import com.k2.music.ChordMatch
import com.k2.music.ChordRepository
import com.k2.music.ChordTransposer
import com.k2.music.CustomVoicing
import com.k2.music.CustomVoicingStore
import com.k2.music.MusicTheoryUtils
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.model.toUiModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class RecognitionMatchUi(
    val symbol: String,
    val chineseName: String,
    val score: Int,
    val matchLabel: String,
    val chordNotes: List<String>,
    val actualNotes: List<String>,
    val missingNotes: List<String>,
    val extraNotes: List<String>,
    val inversion: Boolean,
    val bassNote: String,
    val chord: ChordUiModel,
)

data class CustomVoicingDraft(
    val chordSymbol: String,
    val name: String,
    val frets: List<Int>,
    val fingers: List<Int> = emptyList(),
    val startFret: Int = 1,
    val note: String = "",
)

interface RecognitionGateway {
    suspend fun identifyFrets(frets: List<Int>): List<RecognitionMatchUi>
    suspend fun identifyNotes(notes: String): List<RecognitionMatchUi>
    suspend fun saveCustom(draft: CustomVoicingDraft): CustomVoicing
}

class DefaultRecognitionGateway(
    private val identifier: ChordIdentifier,
    private val repository: ChordRepository,
    private val customStore: CustomVoicingStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : RecognitionGateway {
    override suspend fun identifyFrets(frets: List<Int>): List<RecognitionMatchUi> = withContext(dispatcher) {
        identifier.identifyFrets(frets.map { if (it < -1) -1 else it }.toIntArray()).map { it.toUi() }
    }

    override suspend fun identifyNotes(notes: String): List<RecognitionMatchUi> = withContext(dispatcher) {
        identifier.identifyNotes(notes).map { it.toUi() }
    }

    override suspend fun saveCustom(draft: CustomVoicingDraft): CustomVoicing = withContext(Dispatchers.IO) {
        val lookup = repository.find(draft.chordSymbol)
        require(lookup.recognized && lookup.chord != null) { lookup.message ?: "所属和弦无效" }
        val playable = draft.frets.map { if (it < -1) -1 else it }.toIntArray()
        val matches = identifier.identifyFrets(playable)
        require(matches.any { it.symbol.equals(draft.chordSymbol, ignoreCase = true) || it.chord.symbol.equals(lookup.chord.symbol, ignoreCase = true) }) {
            "当前指板与所选和弦不匹配，请重新识别后保存"
        }
        val fingers = draft.fingers.takeIf { it.size == 6 }?.toIntArray()
        val minimumFret = playable.filter { it > 0 }.minOrNull() ?: 1
        val startFret = draft.startFret.coerceIn(1, 26).let { requested ->
            // Low-position shapes should keep the nut visible. For higher shapes, do not
            // allow a user-entered window to hide the first fretted note.
            if (minimumFret <= 4) 1 else requested.coerceAtMost(minimumFret)
        }
        customStore.save(
            CustomVoicing(
                null,
                lookup.chord.symbol,
                draft.name,
                playable,
                fingers,
                startFret,
                draft.note.ifBlank { "由反向识别保存" },
                System.currentTimeMillis(),
            ),
        )
    }

    private fun ChordMatch.toUi() = RecognitionMatchUi(
        symbol = symbol,
        chineseName = chineseName,
        score = score,
        matchLabel = matchLabel(),
        chordNotes = chordNotes.toList(),
        actualNotes = actualNotes.toList(),
        missingNotes = missingNotes.toList(),
        extraNotes = extraNotes.toList(),
        inversion = inversion,
        bassNote = bassNote,
        chord = chord.toUiModel(),
    )
}

data class CapoSuggestionUi(
    val capoFret: Int,
    val shapes: List<String>,
    val soundingChords: List<String>,
)

interface TransposeGateway {
    suspend fun transpose(progression: String, semitones: Int, preference: MusicTheoryUtils.AccidentalPreference): String
    suspend fun sounding(shape: String, capoFret: Int, preference: MusicTheoryUtils.AccidentalPreference): String
    suspend fun shapeFor(sounding: String, capoFret: Int, preference: MusicTheoryUtils.AccidentalPreference): String
    suspend fun matchingCapos(actual: String, shapes: String): List<CapoSuggestionUi>
    suspend fun splitProgression(value: String): List<String>
}

class DefaultTransposeGateway(
    private val transposer: ChordTransposer,
    private val capoAssistant: CapoAssistant,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : TransposeGateway {
    override suspend fun transpose(
        progression: String,
        semitones: Int,
        preference: MusicTheoryUtils.AccidentalPreference,
    ): String = withContext(dispatcher) {
        transposer.transposeProgression(progression, semitones, preference)
    }

    override suspend fun sounding(
        shape: String,
        capoFret: Int,
        preference: MusicTheoryUtils.AccidentalPreference,
    ): String = withContext(dispatcher) {
        capoAssistant.soundingProgression(shape, capoFret, preference)
    }

    override suspend fun shapeFor(
        sounding: String,
        capoFret: Int,
        preference: MusicTheoryUtils.AccidentalPreference,
    ): String = withContext(dispatcher) {
        transposer.splitProgression(sounding).joinToString(" ") {
            capoAssistant.shapeForSoundingChord(it, capoFret, preference)
        }
    }

    override suspend fun matchingCapos(actual: String, shapes: String): List<CapoSuggestionUi> =
        withContext(dispatcher) {
            capoAssistant.findMatchingCapos(actual, shapes).map {
                CapoSuggestionUi(it.capoFret, it.shapes.toList(), it.soundingChords.toList())
            }
        }

    override suspend fun splitProgression(value: String): List<String> = withContext(dispatcher) {
        transposer.splitProgression(value)
    }
}

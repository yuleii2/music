package com.k2.music.ui.gateway

import com.k2.music.ChordAudioPlayer
import com.k2.music.ChordRepository
import com.k2.music.CustomVoicing
import com.k2.music.CustomVoicingStore
import com.k2.music.PracticePreferencesStore
import com.k2.music.UserChordStore
import com.k2.music.VoicingRecommendationEngine
import com.k2.music.ui.model.ChordLookupUiResult
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.model.VoicingUiModel
import com.k2.music.ui.model.toUiModel
import java.io.Closeable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryFilter(
    val root: String = "",
    val qualityId: String = "",
    val difficultyBucket: Int = 0,
    val openOnly: Boolean = false,
    val barreOnly: Boolean = false,
    val simplifiedOnly: Boolean = false,
) {
    val isActive: Boolean
        get() = root.isNotEmpty() || qualityId.isNotEmpty() || difficultyBucket > 0 ||
            openOnly || barreOnly || simplifiedOnly
}

interface ChordCatalogGateway {
    suspend fun allChords(): List<ChordUiModel>
    suspend fun search(query: String, filter: LibraryFilter = LibraryFilter()): List<ChordUiModel>
    suspend fun find(rawSymbol: String): ChordLookupUiResult
    suspend fun examples(): List<String>
    suspend fun roots(): List<String>
    suspend fun qualities(): List<Pair<String, String>>
    fun usesFallbackData(): Boolean
    fun dataLoadMessage(): String
}

class DefaultChordCatalogGateway(
    private val repository: ChordRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : ChordCatalogGateway {
    override suspend fun allChords(): List<ChordUiModel> = withContext(dispatcher) {
        repository.allChords().map { it.toUiModel() }
    }

    override suspend fun search(query: String, filter: LibraryFilter): List<ChordUiModel> =
        withContext(dispatcher) {
            val normalizedQuery = query.trim()
            val source = if (normalizedQuery.isEmpty() && !filter.isActive) {
                repository.allChords()
            } else {
                repository.filteredChords(
                    normalizedQuery,
                    filter.root,
                    filter.qualityId,
                    filter.difficultyBucket,
                )
            }
            source.asSequence()
                .map { it.toUiModel() }
                .filter { chord ->
                    val voicings = chord.voicings
                    (!filter.openOnly || voicings.any { it.isOpen }) &&
                        (!filter.barreOnly || voicings.any { it.barre }) &&
                        (!filter.simplifiedOnly || voicings.any { it.simplified })
                }
                .distinctBy { it.symbol }
                .toList()
        }

    override suspend fun find(rawSymbol: String): ChordLookupUiResult = withContext(dispatcher) {
        val result = repository.find(rawSymbol)
        if (result.recognized && result.chord != null) {
            ChordLookupUiResult(true, result.chord.toUiModel(), result.message)
        } else {
            ChordLookupUiResult(false, message = result.message)
        }
    }

    override suspend fun examples(): List<String> = withContext(dispatcher) { repository.examples().toList() }

    override suspend fun roots(): List<String> = withContext(dispatcher) {
        repository.allChords().map { it.root }.distinct()
    }

    override suspend fun qualities(): List<Pair<String, String>> = withContext(dispatcher) {
        repository.allQualities.map { it.id to it.chineseName }
    }

    override fun usesFallbackData(): Boolean = repository.isUsingFallbackData

    override fun dataLoadMessage(): String = repository.dataLoadMessage
}

interface UserLibraryGateway {
    suspend fun favorites(): List<String>
    suspend fun history(): List<String>
    suspend fun customVoicings(): List<CustomVoicing>
    suspend fun customVoicings(symbol: String): List<CustomVoicing>
    suspend fun isFavorite(symbol: String): Boolean
    suspend fun toggleFavorite(symbol: String): Boolean
    suspend fun addHistory(symbol: String)
    suspend fun removeHistory(symbol: String): Boolean
    suspend fun deleteCustomVoicing(id: String): Boolean
    suspend fun isFamiliar(chord: ChordUiModel, voicing: VoicingUiModel): Boolean
    suspend fun toggleFamiliar(chord: ChordUiModel, voicing: VoicingUiModel): Boolean
}

class DefaultUserLibraryGateway(
    private val userStore: UserChordStore,
    private val customStore: CustomVoicingStore,
    private val practicePreferencesStore: PracticePreferencesStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : UserLibraryGateway {
    override suspend fun favorites(): List<String> = withContext(dispatcher) { userStore.favorites() }
    override suspend fun history(): List<String> = withContext(dispatcher) { userStore.history() }
    override suspend fun customVoicings(): List<CustomVoicing> = withContext(dispatcher) { customStore.all() }
    override suspend fun customVoicings(symbol: String): List<CustomVoicing> = withContext(dispatcher) {
        customStore.forChord(symbol)
    }
    override suspend fun isFavorite(symbol: String): Boolean = withContext(dispatcher) {
        userStore.isFavorite(symbol)
    }
    override suspend fun toggleFavorite(symbol: String): Boolean = withContext(dispatcher) {
        userStore.toggleFavorite(symbol)
    }
    override suspend fun addHistory(symbol: String) = withContext(dispatcher) { userStore.addHistory(symbol) }
    override suspend fun removeHistory(symbol: String): Boolean = withContext(dispatcher) {
        userStore.removeHistory(symbol)
    }
    override suspend fun deleteCustomVoicing(id: String): Boolean = withContext(dispatcher) {
        customStore.delete(id)
    }
    override suspend fun isFamiliar(chord: ChordUiModel, voicing: VoicingUiModel): Boolean =
        withContext(dispatcher) {
            practicePreferencesStore.load().familiarVoicingIds.contains(voicingKey(chord, voicing))
        }

    override suspend fun toggleFamiliar(chord: ChordUiModel, voicing: VoicingUiModel): Boolean =
        withContext(dispatcher) {
            val preferences = practicePreferencesStore.load()
            val key = voicingKey(chord, voicing)
            val familiar = !preferences.familiarVoicingIds.contains(key)
            practicePreferencesStore.save(preferences.withFamiliarVoicing(key, familiar))
            familiar
        }

    private fun voicingKey(chord: ChordUiModel, voicing: VoicingUiModel): String {
        val coreVoicing = com.k2.music.Voicing(
            voicing.name,
            voicing.frets.toIntArray(),
            voicing.fingers.toIntArray(),
            voicing.startFret,
            voicing.displayFrets,
            voicing.difficulty,
            voicing.recommended,
            voicing.simplified,
            voicing.barre,
            voicing.description,
        )
        return VoicingRecommendationEngine.voicingId(chord.symbol, coreVoicing)
    }
}

sealed interface PlaybackUiState {
    data object Idle : PlaybackUiState
    data class Preparing(val symbol: String) : PlaybackUiState
    data class Playing(val symbol: String, val voicingName: String) : PlaybackUiState
    data class Failed(val symbol: String, val message: String) : PlaybackUiState
}

interface ChordPlaybackController {
    val state: StateFlow<PlaybackUiState>
    fun play(chord: ChordUiModel, voicing: VoicingUiModel?, arpeggio: Boolean = false)
    fun stop()
}

class PlaybackController(
    private val player: ChordAudioPlayer,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val beforePlay: () -> Unit = {},
) : ChordPlaybackController, Closeable {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)
    private val _state = MutableStateFlow<PlaybackUiState>(PlaybackUiState.Idle)
    private var playJob: Job? = null
    override val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    override fun play(chord: ChordUiModel, voicing: VoicingUiModel?, arpeggio: Boolean) {
        beforePlay()
        playJob?.cancel()
        playJob = scope.launch {
            _state.value = PlaybackUiState.Preparing(chord.symbol)
            val notes = voicing?.playableMidiNotes?.takeIf { it.isNotEmpty() }
                ?: chord.notes.mapNotNull { note ->
                    runCatching { com.k2.music.NoteUtils.noteNameToMiddleMidi(note) }.getOrNull()
                }.toIntArray()
            val success = if (arpeggio) player.playArpeggio(notes) else player.play(notes)
            _state.value = if (success) {
                PlaybackUiState.Playing(chord.symbol, voicing?.name ?: "组成音")
            } else {
                PlaybackUiState.Failed(chord.symbol, "试听暂不可用，请检查设备音频状态。")
            }
        }
    }

    override fun stop() {
        playJob?.cancel()
        player.stop()
        _state.value = PlaybackUiState.Idle
    }

    override fun close() {
        stop()
        scope.cancel()
    }
}

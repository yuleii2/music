package com.k2.music.ui.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2.music.ui.gateway.ChordCatalogGateway
import com.k2.music.ui.gateway.UserLibraryGateway
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.gateway.PracticeGateway
import com.k2.music.ui.gateway.PracticeSummaryUi
import com.k2.music.ui.learning.DailyPracticePlan
import com.k2.music.ui.learning.DailyTaskType
import com.k2.music.ui.learning.LearningProfile
import com.k2.music.ui.learning.LearningGoal
import com.k2.music.ui.song.SongGateway
import com.k2.music.ui.song.SongHomeTask
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val fallbackMessage: String? = null,
    val searchActive: Boolean = false,
    val searchQuery: String = "",
    val searching: Boolean = false,
    val searchResults: List<ChordUiModel> = emptyList(),
    val searchError: String? = null,
    val recent: List<ChordUiModel> = emptyList(),
    val recommendations: List<ChordUiModel> = emptyList(),
    val practiceSummary: PracticeSummaryUi = PracticeSummaryUi(),
    val dailyPlan: DailyPracticePlan? = null,
    val songTasks: List<SongHomeTask> = emptyList(),
    val songTaskStarting: Boolean = false,
    val songTaskError: String? = null,
)

sealed interface HomeEffect {
    data class NavigateToChord(val symbol: String) : HomeEffect
    data class RecentRemoved(val symbol: String) : HomeEffect
    data class NavigateToSongTask(val task: SongHomeTask) : HomeEffect
}

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val catalog: ChordCatalogGateway,
    private val userLibrary: UserLibraryGateway,
    private val practiceGateway: PracticeGateway,
    private val learningProfile: () -> LearningProfile,
    private val savedStateHandle: SavedStateHandle,
    private val songGateway: SongGateway? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        HomeUiState(
            searchActive = savedStateHandle[KEY_SEARCH_ACTIVE] ?: false,
            searchQuery = savedStateHandle[KEY_QUERY] ?: "",
        ),
    )
    private val effectsChannel = Channel<HomeEffect>(Channel.BUFFERED)

    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    val effects = effectsChannel.receiveAsFlow()

    init {
        loadContent()
        viewModelScope.launch {
            savedStateHandle.getStateFlow(KEY_QUERY, "")
                .debounce(140)
                .mapLatest { query ->
                    if (query.isBlank()) emptyList() else catalog.search(query).take(40)
                }
                .collect { results ->
                    _state.value = _state.value.copy(
                        searching = false,
                        searchResults = decorate(results),
                        searchError = null,
                    )
                }
        }
    }

    fun openSearch() {
        savedStateHandle[KEY_SEARCH_ACTIVE] = true
        _state.value = _state.value.copy(searchActive = true)
    }

    fun closeSearch() {
        savedStateHandle[KEY_SEARCH_ACTIVE] = false
        savedStateHandle[KEY_QUERY] = ""
        _state.value = _state.value.copy(
            searchActive = false,
            searchQuery = "",
            searchResults = emptyList(),
            searchError = null,
        )
    }

    fun updateQuery(value: String) {
        savedStateHandle[KEY_QUERY] = value
        _state.value = _state.value.copy(
            searchQuery = value,
            searching = value.isNotBlank(),
            searchError = null,
        )
    }

    fun submitSearch() {
        val query = _state.value.searchQuery.trim()
        if (query.isEmpty()) {
            _state.value = _state.value.copy(searchError = "请输入和弦名称，例如 C、Am、G7 或 Fmaj7。")
            return
        }
        viewModelScope.launch {
            val result = catalog.find(query)
            if (result.recognized && result.chord != null) {
                userLibrary.addHistory(result.chord.symbol)
                effectsChannel.send(HomeEffect.NavigateToChord(result.chord.symbol))
                loadContent()
            } else {
                _state.value = _state.value.copy(searchError = result.message ?: "无法识别该和弦名称。")
            }
        }
    }

    fun openChord(symbol: String) {
        viewModelScope.launch {
            userLibrary.addHistory(symbol)
            effectsChannel.send(HomeEffect.NavigateToChord(symbol))
            loadContent()
        }
    }

    fun removeRecent(symbol: String) {
        viewModelScope.launch {
            if (userLibrary.removeHistory(symbol)) {
                loadContent()
                effectsChannel.send(HomeEffect.RecentRemoved(symbol))
            }
        }
    }

    fun restoreRecent(symbol: String) {
        viewModelScope.launch {
            userLibrary.addHistory(symbol)
            loadContent()
        }
    }

    fun toggleFavorite(symbol: String) {
        viewModelScope.launch {
            userLibrary.toggleFavorite(symbol)
            loadContent()
            if (_state.value.searchQuery.isNotBlank()) {
                val results = catalog.search(_state.value.searchQuery).take(40)
                _state.value = _state.value.copy(searchResults = decorate(results))
            }
        }
    }

    fun refresh() = loadContent()

    fun startSongTask(task: SongHomeTask) {
        val gateway = songGateway ?: return
        if (_state.value.songTaskStarting) return
        viewModelScope.launch {
            _state.value = _state.value.copy(songTaskStarting = true, songTaskError = null)
            runCatching {
                gateway.restorePracticeConfiguration(
                    songId = task.songId,
                    bpm = task.bpm,
                    transposeSemitones = task.transposeSemitones,
                    capoFret = task.capoFret,
                    selectedVoicingIds = task.selectedVoicingIds,
                    restoreVoicings = true,
                )
            }.onSuccess {
                _state.value = _state.value.copy(songTaskStarting = false)
                effectsChannel.send(HomeEffect.NavigateToSongTask(task))
            }.onFailure { error ->
                _state.value = _state.value.copy(
                    songTaskStarting = false,
                    songTaskError = error.message ?: "无法恢复上次曲谱练习设置。",
                )
            }
        }
    }

    private fun loadContent() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val recentSymbols = userLibrary.history().take(8)
            val recent = recentSymbols.mapNotNull { catalog.find(it).chord }
            val favorites = userLibrary.favorites().toSet()
            val allChords = catalog.allChords()
            val profile = learningProfile()
            val plan = practiceGateway.dailyPlan(profile, favorites, allChords)
            val songTasksResult = runCatching {
                songGateway?.homeTasks(LearningGoal.SONG_ACCOMPANIMENT in profile.goals).orEmpty()
            }
            val recommendationSymbols = plan.tasks
                .filter { it.type == DailyTaskType.LEARN_NEW_CHORD }
                .mapNotNull { it.chordSymbol }
            val recommendations = recommendationSymbols.mapNotNull { catalog.find(it).chord }
            val practiceSummary = practiceGateway.summary()
            _state.value = _state.value.copy(
                loading = false,
                fallbackMessage = catalog.dataLoadMessage().takeIf { catalog.usesFallbackData() },
                recent = decorate(recent),
                recommendations = decorate(recommendations),
                practiceSummary = practiceSummary,
                dailyPlan = plan,
                songTasks = songTasksResult.getOrDefault(emptyList()),
                songTaskError = songTasksResult.exceptionOrNull()?.message,
            )
        }
    }

    private suspend fun decorate(chords: List<ChordUiModel>): List<ChordUiModel> =
        chords.map { chord -> chord.copy(favorite = userLibrary.isFavorite(chord.symbol)) }

    private companion object {
        const val KEY_QUERY = "home_query"
        const val KEY_SEARCH_ACTIVE = "home_search_active"
    }
}

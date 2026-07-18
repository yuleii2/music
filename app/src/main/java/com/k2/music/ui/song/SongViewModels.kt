package com.k2.music.ui.song

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.k2.music.song.SongProject
import com.k2.music.song.SongSection
import com.k2.music.song.SongTimingState
import com.k2.music.song.SongArrangement
import com.k2.music.song.SongParseLineRole
import com.k2.music.MusicTheoryUtils
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SongLibraryUiState(
    val loading: Boolean = true,
    val query: String = "",
    val data: SongLibraryData? = null,
    val error: String? = null,
)

class SongLibraryViewModel(private val gateway: SongGateway) : ViewModel() {
    private val _state = MutableStateFlow(SongLibraryUiState())
    val state: StateFlow<SongLibraryUiState> = _state.asStateFlow()

    init { refresh() }

    fun setQuery(value: String) {
        _state.update { it.copy(query = value) }
        refresh()
    }

    fun refresh() {
        val query = _state.value.query
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { gateway.library(query) }
                .onSuccess { data -> _state.update { it.copy(loading = false, data = data) } }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.userMessage("无法读取曲谱库。")) }
                }
        }
    }
}

data class SongImportUiState(
    val title: String = "",
    val artist: String = "",
    val originalText: String = "",
    val timeSignature: String = "4/4",
    val parsing: Boolean = false,
    val message: String? = null,
)

sealed interface SongImportEffect { data object OpenPreview : SongImportEffect }

class SongImportViewModel(
    private val gateway: SongGateway,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(
        SongImportUiState(
            title = savedStateHandle["song_import_title"] ?: "",
            artist = savedStateHandle["song_import_artist"] ?: "",
            originalText = savedStateHandle["song_import_text"] ?: "",
            timeSignature = savedStateHandle["song_import_signature"] ?: "4/4",
        ),
    )
    val state = _state.asStateFlow()
    private val _effects = MutableSharedFlow<SongImportEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<SongImportEffect> = _effects.asSharedFlow()

    fun setTitle(value: String) = update("song_import_title", value) { copy(title = value, message = null) }
    fun setArtist(value: String) = update("song_import_artist", value) { copy(artist = value, message = null) }
    fun setOriginalText(value: String) = update("song_import_text", value) { copy(originalText = value, message = null) }
    fun setTimeSignature(value: String) = update("song_import_signature", value) { copy(timeSignature = value, message = null) }

    fun parse() {
        if (_state.value.parsing) return
        val input = _state.value
        viewModelScope.launch {
            _state.update { it.copy(parsing = true, message = null) }
            runCatching {
                gateway.parseImport(input.title, input.artist, input.originalText, input.timeSignature)
            }.onSuccess { draft ->
                _state.update {
                    it.copy(
                        parsing = false,
                        title = draft.title,
                        message = draft.parseResult.warnings.firstOrNull()?.message,
                    )
                }
                _effects.emit(SongImportEffect.OpenPreview)
            }.onFailure { error ->
                _state.update { it.copy(parsing = false, message = error.userMessage("解析失败，请检查曲谱原文。")) }
            }
        }
    }

    private fun update(
        key: String,
        value: String,
        reducer: SongImportUiState.() -> SongImportUiState,
    ) {
        savedStateHandle[key] = value
        _state.update { it.reducer() }
    }
}

data class SongPreviewUiState(
    val loading: Boolean = true,
    val draft: SongImportDraft? = null,
    val saving: Boolean = false,
    val error: String? = null,
    val correctingLine: Int? = null,
)

sealed interface SongPreviewEffect { data class Saved(val songId: String) : SongPreviewEffect }

class SongPreviewViewModel(private val gateway: SongGateway) : ViewModel() {
    private val _state = MutableStateFlow(SongPreviewUiState())
    val state = _state.asStateFlow()
    private val _effects = MutableSharedFlow<SongPreviewEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { gateway.importDraft() }
                .onSuccess { draft ->
                    _state.update {
                        it.copy(
                            loading = false,
                            draft = draft,
                            error = if (draft == null) "导入草稿已失效，请返回重新解析。" else null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.userMessage("无法读取导入预览。")) }
                }
        }
    }

    fun save() {
        if (_state.value.saving || _state.value.draft == null) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching { gateway.saveImportDraft() }
                .onSuccess { project ->
                    _state.update { it.copy(saving = false) }
                    _effects.emit(SongPreviewEffect.Saved(project.id))
                }
                .onFailure { error ->
                    _state.update { it.copy(saving = false, error = error.userMessage("无法保存曲谱。")) }
                }
        }
    }

    fun setLineRole(lineNumber: Int, role: SongParseLineRole) {
        if (_state.value.correctingLine != null) return
        viewModelScope.launch {
            _state.update { it.copy(correctingLine = lineNumber, error = null) }
            runCatching { gateway.setImportLineRole(lineNumber, role) }
                .onSuccess { draft -> _state.update { it.copy(draft = draft, correctingLine = null) } }
                .onFailure { error ->
                    _state.update {
                        it.copy(correctingLine = null, error = error.userMessage("无法应用这一行的解析修正。"))
                    }
                }
        }
    }
}

data class SongDetailUiState(
    val loading: Boolean = true,
    val data: SongDetailData? = null,
    val arrangement: SongArrangement? = null,
    val arranging: Boolean = false,
    val deleting: Boolean = false,
    val error: String? = null,
)

sealed interface SongDetailEffect { data object Deleted : SongDetailEffect }

class SongDetailViewModel(
    private val gateway: SongGateway,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val songId: String = savedStateHandle["id"] ?: ""
    private val _state = MutableStateFlow(SongDetailUiState())
    val state = _state.asStateFlow()
    private val _effects = MutableSharedFlow<SongDetailEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { gateway.detail(songId) to gateway.arrangement(songId) }
                .onSuccess { (data, arrangement) ->
                    _state.update {
                        it.copy(
                            loading = false,
                            data = data,
                            arrangement = arrangement,
                            error = if (data == null) "这份曲谱不存在或已被删除。" else null,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(loading = false, error = error.userMessage("无法读取曲谱。")) }
                }
        }
    }

    fun setTranspose(semitones: Int) = updateArrangement { project ->
        Triple(semitones.coerceIn(-11, 11), project.capoFret, project.accidentalPreference)
    }

    fun setCapo(capoFret: Int) = updateArrangement { project ->
        Triple(project.transposeSemitones, capoFret.coerceIn(0, 12), project.accidentalPreference)
    }

    fun setAccidentalPreference(preference: MusicTheoryUtils.AccidentalPreference) = updateArrangement { project ->
        Triple(project.transposeSemitones, project.capoFret, preference)
    }

    fun resetArrangement() {
        if (_state.value.arranging) return
        viewModelScope.launch {
            _state.update { it.copy(arranging = true, error = null) }
            runCatching { gateway.resetArrangement(songId) }
                .onSuccess { reloadAfterArrangement() }
                .onFailure { error ->
                    _state.update { it.copy(arranging = false, error = error.userMessage("无法恢复原调。")) }
                }
        }
    }

    fun pinVoicing(eventId: String, voicingId: String?) {
        if (_state.value.arranging) return
        viewModelScope.launch {
            _state.update { it.copy(arranging = true, error = null) }
            runCatching { gateway.pinVoicing(songId, eventId, voicingId) }
                .onSuccess { reloadAfterArrangement() }
                .onFailure { error ->
                    _state.update { it.copy(arranging = false, error = error.userMessage("无法固定该指法。")) }
                }
        }
    }

    private fun updateArrangement(
        transform: (SongProject) -> Triple<Int, Int, MusicTheoryUtils.AccidentalPreference>,
    ) {
        val project = _state.value.data?.project ?: return
        if (_state.value.arranging) return
        val (semitones, capo, preference) = transform(project)
        viewModelScope.launch {
            _state.update { it.copy(arranging = true, error = null) }
            runCatching { gateway.configureArrangement(songId, semitones, capo, preference) }
                .onSuccess { reloadAfterArrangement() }
                .onFailure { error ->
                    _state.update { it.copy(arranging = false, error = error.userMessage("无法更新曲谱调性设置。")) }
                }
        }
    }

    private suspend fun reloadAfterArrangement() {
        runCatching { gateway.detail(songId) to gateway.arrangement(songId) }
            .onSuccess { (data, arrangement) ->
                _state.update {
                    it.copy(data = data, arrangement = arrangement, arranging = false, loading = false)
                }
            }
            .onFailure { error ->
                _state.update { it.copy(arranging = false, error = error.userMessage("设置已保存，但刷新失败。")) }
            }
    }

    fun delete() {
        if (_state.value.deleting) return
        viewModelScope.launch {
            _state.update { it.copy(deleting = true, error = null) }
            runCatching { gateway.deleteProject(songId) }
                .onSuccess {
                    _state.update { it.copy(deleting = false) }
                    _effects.emit(SongDetailEffect.Deleted)
                }
                .onFailure { error ->
                    _state.update { it.copy(deleting = false, error = error.userMessage("删除曲谱失败。")) }
                }
        }
    }
}

data class SongEditorUiState(
    val loading: Boolean = true,
    val project: SongProject? = null,
    val title: String = "",
    val artist: String = "",
    val originalText: String = "",
    val originalKey: String = "",
    val bpmText: String = "80",
    val timeSignature: String = "4/4",
    val notes: String = "",
    val sections: List<SongSection> = emptyList(),
    val dirty: Boolean = false,
    val saving: Boolean = false,
    val reparsing: Boolean = false,
    val error: String? = null,
)

sealed interface SongEditorEffect {
    data class Saved(val songId: String) : SongEditorEffect
}

class SongEditorViewModel(
    private val gateway: SongGateway,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val songId: String = savedStateHandle["id"] ?: ""
    private val _state = MutableStateFlow(SongEditorUiState())
    val state = _state.asStateFlow()
    private val _effects = MutableSharedFlow<SongEditorEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    init { load() }

    private fun load() {
        viewModelScope.launch {
            runCatching {
                if (songId.isBlank() || songId == "new") gateway.createManual() else gateway.detail(songId)?.project
            }.onSuccess { project ->
                if (project == null) {
                    _state.value = SongEditorUiState(loading = false, error = "这份曲谱不存在或已被删除。")
                } else {
                    _state.value = project.toEditorState()
                }
            }.onFailure { error ->
                _state.value = SongEditorUiState(loading = false, error = error.userMessage("无法打开曲谱编辑器。"))
            }
        }
    }

    fun setTitle(value: String) = edit { copy(title = value) }
    fun setArtist(value: String) = edit { copy(artist = value) }
    fun setOriginalText(value: String) = edit { copy(originalText = value) }
    fun setOriginalKey(value: String) = edit { copy(originalKey = value) }
    fun setBpm(value: String) = edit { copy(bpmText = value.filter(Char::isDigit).take(3)) }
    fun setTimeSignature(value: String) = edit { copy(timeSignature = value) }
    fun setNotes(value: String) = edit { copy(notes = value) }

    fun setSectionName(index: Int, value: String) = editSections { sections ->
        sections.mapIndexed { position, section ->
            if (position == index && value.isNotBlank()) section.copy(name = value) else section
        }
    }

    fun setSectionRepeat(index: Int, value: Int) = editSections { sections ->
        sections.mapIndexed { position, section ->
            if (position == index) section.copy(repeatCount = value.coerceIn(1, 99)) else section
        }
    }

    fun moveSection(index: Int, delta: Int) = editSections { sections ->
        if (index !in sections.indices) return@editSections sections
        val target = (index + delta).coerceIn(sections.indices)
        if (target == index) sections else sections.toMutableList().apply {
            add(target, removeAt(index))
        }.mapIndexed { order, section -> section.copy(order = order) }
    }

    fun deleteSection(index: Int) = editSections { sections ->
        sections.filterIndexed { position, _ -> position != index }
            .mapIndexed { order, section -> section.copy(order = order) }
    }

    fun setEventDuration(sectionIndex: Int, rowIndex: Int, eventIndex: Int, value: String) {
        val duration = value.toDoubleOrNull()
        if (value.isNotBlank() && (duration == null || duration <= 0.0 || duration > 64.0)) {
            _state.update { it.copy(error = "持续拍数必须大于 0 且不超过 64。") }
            return
        }
        editSections { sections ->
            sections.mapIndexed { si, section ->
                if (si != sectionIndex) section else section.copy(
                    rows = section.rows.mapIndexed { ri, row ->
                        if (ri != rowIndex) row else row.copy(
                            chordEvents = row.chordEvents.mapIndexed { ei, event ->
                                if (ei == eventIndex) event.copy(durationBeats = duration) else event
                            },
                        )
                    },
                )
            }
        }
    }

    fun setEventVoicing(sectionIndex: Int, rowIndex: Int, eventIndex: Int, value: String) = editSections { sections ->
        sections.mapIndexed { si, section ->
            if (si != sectionIndex) section else section.copy(
                rows = section.rows.mapIndexed { ri, row ->
                    if (ri != rowIndex) row else row.copy(
                        chordEvents = row.chordEvents.mapIndexed { ei, event ->
                            if (ei == eventIndex) event.copy(selectedVoicingId = value.trim().ifBlank { null }) else event
                        },
                    )
                },
            )
        }
    }

    fun reparse() {
        val current = _state.value
        if (current.reparsing) return
        viewModelScope.launch {
            _state.update { it.copy(reparsing = true, error = null) }
            runCatching { gateway.parseText(current.originalText, current.timeSignature) }
                .onSuccess { result ->
                    _state.update {
                        it.copy(
                            reparsing = false,
                            sections = result.sections,
                            dirty = true,
                            error = result.warnings.firstOrNull()?.message,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(reparsing = false, error = error.userMessage("重新解析失败。")) }
                }
        }
    }

    fun save() {
        val current = _state.value
        val source = current.project ?: return
        if (current.saving) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null) }
            runCatching {
                require(current.title.isNotBlank()) { "曲名不能为空。" }
                val bpm = current.bpmText.toIntOrNull()
                require(bpm in 40..240) { "BPM 必须在 40 到 240 之间。" }
                val events = current.sections.flatMap { it.rows }.flatMap { it.chordEvents }
                val timing = when {
                    events.isNotEmpty() && events.all { it.durationBeats != null } -> SongTimingState.EXPLICIT_BEATS
                    current.project.timingState == SongTimingState.SIMPLE_MEASURES -> SongTimingState.SIMPLE_MEASURES
                    else -> SongTimingState.UNTYPED
                }
                gateway.saveProject(
                    source.copy(
                        title = current.title.trim(),
                        artist = current.artist.trim(),
                        originalText = current.originalText,
                        originalKey = current.originalKey.trim(),
                        bpm = requireNotNull(bpm),
                        timeSignature = current.timeSignature,
                        timingState = timing,
                        sections = current.sections,
                        notes = current.notes,
                    ),
                )
            }.onSuccess { project ->
                _state.value = project.toEditorState().copy(saving = false)
                _effects.emit(SongEditorEffect.Saved(project.id))
            }.onFailure { error ->
                _state.update { it.copy(saving = false, error = error.userMessage("无法保存曲谱。")) }
            }
        }
    }

    private fun edit(reducer: SongEditorUiState.() -> SongEditorUiState) {
        _state.update { it.reducer().copy(dirty = true, error = null) }
    }

    private fun editSections(reducer: (List<SongSection>) -> List<SongSection>) {
        _state.update { it.copy(sections = reducer(it.sections), dirty = true, error = null) }
    }
}

private fun SongProject.toEditorState() = SongEditorUiState(
    loading = false,
    project = this,
    title = title,
    artist = artist,
    originalText = originalText,
    originalKey = originalKey,
    bpmText = bpm.toString(),
    timeSignature = timeSignature,
    notes = notes,
    sections = sections,
)

internal fun Throwable.userMessage(fallback: String): String =
    message?.takeIf { it.isNotBlank() } ?: fallback

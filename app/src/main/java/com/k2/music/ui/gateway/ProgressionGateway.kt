package com.k2.music.ui.gateway

import com.k2.music.ChordProgression
import com.k2.music.ChordRepository
import com.k2.music.ChordTransposer
import com.k2.music.CustomVoicingStore
import com.k2.music.KeySignature
import com.k2.music.PracticePreferencesStore
import com.k2.music.ProgressionPresetRepository
import com.k2.music.ProgressionStep
import com.k2.music.ProgressionStore
import com.k2.music.TimeSignature
import com.k2.music.VoicingRecommendationEngine
import com.k2.music.VoicingRecommendationMode
import com.k2.music.ui.model.ProgressionPresetUi
import com.k2.music.ui.model.ProgressionStepUi
import com.k2.music.ui.model.ProgressionSummaryUi
import com.k2.music.ui.model.ProgressionUiModel
import com.k2.music.ui.model.ProgressionVoicingOptionUi
import com.k2.music.ui.model.toCore
import com.k2.music.ui.model.toUiModel
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface ProgressionGateway {
    suspend fun list(): List<ProgressionSummaryUi>
    suspend fun presets(keySignature: String): List<ProgressionPresetUi>
    suspend fun createDraft(seed: String = "", name: String = "我的和弦进行"): ProgressionUiModel
    suspend fun createPresetDraft(presetId: String, keySignature: String): ProgressionUiModel
    suspend fun loadEditor(id: String): ProgressionUiModel?
    suspend fun saveDraft(value: ProgressionUiModel)
    suspend fun save(value: ProgressionUiModel): ProgressionUiModel
    suspend fun appendSymbols(value: ProgressionUiModel, symbols: String): ProgressionUiModel
    suspend fun recommend(value: ProgressionUiModel): ProgressionUiModel
    suspend fun duplicate(id: String, name: String): ProgressionSummaryUi
    suspend fun rename(id: String, name: String): ProgressionSummaryUi
    suspend fun delete(id: String): ProgressionUiModel?
    suspend fun restore(value: ProgressionUiModel): ProgressionSummaryUi
}
class DefaultProgressionGateway(
    private val store: ProgressionStore,
    private val draftStore: ProgressionStore,
    private val presetRepository: ProgressionPresetRepository,
    private val chordRepository: ChordRepository,
    private val customVoicingStore: CustomVoicingStore,
    private val transposer: ChordTransposer,
    private val preferencesStore: PracticePreferencesStore,
    private val recommendationEngine: VoicingRecommendationEngine,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ProgressionGateway {
    override suspend fun list(): List<ProgressionSummaryUi> = withContext(dispatcher) {
        store.list().map(::summary)
    }

    override suspend fun presets(keySignature: String): List<ProgressionPresetUi> = withContext(dispatcher) {
        val key = KeySignature.parse(keySignature.ifBlank { "C" })
        presetRepository.all().map { preset ->
            ProgressionPresetUi(
                id = preset.id,
                name = preset.name,
                keySignature = key.tonic,
                symbols = presetRepository.generateChordSymbols(preset.id, key),
                beatsPerChord = preset.beatsPerChord,
            )
        }
    }

    override suspend fun createDraft(seed: String, name: String): ProgressionUiModel = withContext(dispatcher) {
        val now = System.currentTimeMillis()
        val steps = parseSteps(seed)
        val progression = ChordProgression(
            UUID.randomUUID().toString(),
            name.ifBlank { "我的和弦进行" },
            "C",
            TimeSignature.FOUR_FOUR,
            preferencesStore.load().defaultBpm,
            true,
            steps,
            now,
            now,
            "",
        )
        map(progression, saved = false, restoredDraft = false)
    }

    override suspend fun createPresetDraft(
        presetId: String,
        keySignature: String,
    ): ProgressionUiModel = withContext(dispatcher) {
        val core = presetRepository.instantiate(
            presetId,
            KeySignature.parse(keySignature.ifBlank { "C" }),
            preferencesStore.load().defaultBpm,
            System.currentTimeMillis(),
        )
        val ui = map(core, saved = false, restoredDraft = false)
        draftStore.upsert(ui.toCore())
        ui
    }

    override suspend fun loadEditor(id: String): ProgressionUiModel? = withContext(dispatcher) {
        val saved = store.read(id)
        val draft = draftStore.read(id)
        val source = when {
            draft == null -> saved
            saved == null -> draft
            draft.updatedAtEpochMillis >= saved.updatedAtEpochMillis -> draft
            else -> saved
        } ?: return@withContext null
        map(
            source,
            saved = saved != null,
            restoredDraft = draft != null && source.id == draft.id && source.updatedAtEpochMillis == draft.updatedAtEpochMillis,
        )
    }

    override suspend fun saveDraft(value: ProgressionUiModel) = withContext(dispatcher) {
        draftStore.upsert(value.toCore())
        Unit
    }

    override suspend fun save(value: ProgressionUiModel): ProgressionUiModel = withContext(dispatcher) {
        require(value.name.isNotBlank()) { "请输入进行名称。" }
        require(value.steps.isNotEmpty()) { "进行至少需要一个和弦。" }
        val stored = store.upsert(value.toCore())
        draftStore.delete(value.id)
        map(stored, saved = true, restoredDraft = false)
    }

    override suspend fun appendSymbols(
        value: ProgressionUiModel,
        symbols: String,
    ): ProgressionUiModel = withContext(dispatcher) {
        val additions = parseSteps(symbols, value.steps.size)
        require(additions.isNotEmpty()) { "请输入至少一个有效和弦。" }
        val core = value.toCore().withSteps(
            value.toCore().steps + additions,
            System.currentTimeMillis(),
        )
        map(core, saved = value.saved, restoredDraft = false).copy(
            playbackMode = value.playbackMode,
            recommendationMode = value.recommendationMode,
            allowBarre = value.allowBarre,
            maxFret = value.maxFret,
        )
    }

    override suspend fun recommend(value: ProgressionUiModel): ProgressionUiModel = withContext(dispatcher) {
        val core = value.toCore()
        val candidates = linkedMapOf<String, List<com.k2.music.Voicing>>()
        core.steps.forEach { step ->
            val lookup = chordRepository.find(step.chordSymbol)
            if (lookup.recognized && lookup.chord != null) {
                candidates[step.chordSymbol] = customVoicingStore.mergeWithBuiltIns(
                    step.chordSymbol,
                    lookup.chord.voicings,
                )
            }
        }
        val preferences = preferencesStore.load().withVoicingConstraints(value.allowBarre, value.maxFret)
        val recommendations = recommendationEngine.recommend(
            core,
            candidates,
            value.recommendationMode,
            preferences,
        )
        require(recommendations.size == core.steps.size) {
            "部分和弦没有满足横按与最高品位限制的可用按法。"
        }
        val steps = core.steps.mapIndexed { index, step ->
            step.withVoicing(recommendations[index].voicingId)
        }
        map(core.withSteps(steps, System.currentTimeMillis()), value.saved, false).copy(
            playbackMode = value.playbackMode,
            recommendationMode = value.recommendationMode,
            allowBarre = value.allowBarre,
            maxFret = value.maxFret,
            recommendationReasons = recommendations.map { "${it.chordSymbol}：${it.reason}" },
        )
    }

    override suspend fun duplicate(id: String, name: String): ProgressionSummaryUi = withContext(dispatcher) {
        summary(store.duplicate(id, name.ifBlank { "和弦进行副本" }))
    }

    override suspend fun rename(id: String, name: String): ProgressionSummaryUi = withContext(dispatcher) {
        val current = requireNotNull(store.read(id)) { "找不到要重命名的和弦进行。" }
        summary(store.update(current.withName(name, System.currentTimeMillis())))
    }

    override suspend fun delete(id: String): ProgressionUiModel? = withContext(dispatcher) {
        val current = store.read(id) ?: return@withContext null
        if (!store.delete(id)) return@withContext null
        draftStore.delete(id)
        map(current, saved = true, restoredDraft = false)
    }

    override suspend fun restore(value: ProgressionUiModel): ProgressionSummaryUi = withContext(dispatcher) {
        summary(store.upsert(value.toCore()))
    }

    private fun parseSteps(raw: String, startOrder: Int = 0): List<ProgressionStep> {
        if (raw.isBlank()) return emptyList()
        return transposer.splitProgression(raw).mapIndexed { offset, symbol ->
            val lookup = chordRepository.find(symbol)
            require(lookup.recognized && lookup.chord != null) {
                lookup.message ?: "无法识别和弦：$symbol"
            }
            val normalized = lookup.chord.symbol
            val candidates = customVoicingStore.mergeWithBuiltIns(normalized, lookup.chord.voicings)
            val firstId = candidates.firstOrNull()?.let {
                VoicingRecommendationEngine.voicingId(normalized, it)
            }.orEmpty()
            ProgressionStep(normalized, firstId, 4.0, "", startOrder + offset)
        }
    }

    private fun map(
        progression: ChordProgression,
        saved: Boolean,
        restoredDraft: Boolean,
    ): ProgressionUiModel {
        val preferences = preferencesStore.load()
        val steps = progression.steps.map { step ->
            val lookup = chordRepository.find(step.chordSymbol)
            if (!lookup.recognized || lookup.chord == null) {
                ProgressionStepUi(
                    step.chordSymbol,
                    step.voicingId,
                    step.beats,
                    step.strumPattern,
                    step.order,
                    null,
                    emptyList(),
                )
            } else {
                val custom = customVoicingStore.forChord(step.chordSymbol)
                val uiChord = lookup.chord.toUiModel(customVoicings = custom)
                val coreVoicings = customVoicingStore.mergeWithBuiltIns(step.chordSymbol, lookup.chord.voicings)
                val options = coreVoicings.zip(uiChord.voicings).map { (core, ui) ->
                    ProgressionVoicingOptionUi(
                        VoicingRecommendationEngine.voicingId(step.chordSymbol, core),
                        ui,
                    )
                }
                ProgressionStepUi(
                    step.chordSymbol,
                    step.voicingId.ifBlank { options.firstOrNull()?.id.orEmpty() },
                    step.beats,
                    step.strumPattern,
                    step.order,
                    uiChord,
                    options,
                )
            }
        }
        return ProgressionUiModel(
            id = progression.id,
            name = progression.name,
            keySignature = progression.keySignature,
            timeSignature = progression.timeSignature.toString(),
            bpm = progression.bpm,
            loop = progression.loop,
            steps = steps,
            createdAtEpochMillis = progression.createdAtEpochMillis,
            updatedAtEpochMillis = progression.updatedAtEpochMillis,
            notes = progression.notes,
            saved = saved,
            restoredDraft = restoredDraft,
            playbackMode = when (preferences.defaultPlaybackMode) {
                com.k2.music.PracticePreferences.PlaybackMode.WHOLE_CHORD ->
                    com.k2.music.ui.model.ProgressionPlaybackMode.WHOLE_CHORD
                com.k2.music.PracticePreferences.PlaybackMode.ARPEGGIO ->
                    com.k2.music.ui.model.ProgressionPlaybackMode.ARPEGGIO
            },
            allowBarre = preferences.allowBarre,
            maxFret = preferences.maxFret,
        )
    }

    private fun summary(value: ChordProgression) = ProgressionSummaryUi(
        id = value.id,
        name = value.name,
        keySignature = value.keySignature,
        bpm = value.bpm,
        timeSignature = value.timeSignature.toString(),
        stepCount = value.steps.size,
        symbols = value.steps.joinToString(" ") { it.chordSymbol },
        updatedAtEpochMillis = value.updatedAtEpochMillis,
    )
}

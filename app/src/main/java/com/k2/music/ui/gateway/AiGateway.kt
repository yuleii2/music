package com.k2.music.ui.gateway

import com.k2.music.AiChordRecommendationResult
import com.k2.music.AiError
import com.k2.music.AiPracticePlanResult
import com.k2.music.AiProgressionOptimizationResult
import com.k2.music.AiProgressionResult
import com.k2.music.AiPromptFactory
import com.k2.music.AiResultCache
import com.k2.music.AiResultValidator
import com.k2.music.AiService
import com.k2.music.AiSettings
import com.k2.music.AiSettingsStore
import com.k2.music.ChordRepository
import com.k2.music.OpenAiCompatibleProvider
import com.k2.music.PracticePlanDraftStore
import java.util.UUID
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

enum class AiTaskUi(val label: String, val helper: String) {
    RECOMMEND_CHORDS("推荐和弦", "描述你想要的听感或练习目标"),
    GENERATE_PROGRESSION("推荐进行", "描述调性、风格或情绪"),
    EXPLAIN_CHORD("解释和弦", "输入需要解释的和弦"),
    OPTIMIZE_PROGRESSION("优化进行", "输入现有进行与优化目标"),
    PRACTICE_PLAN("练习计划", "确认将发送的本地统计摘要"),
    TRANSITION_ADVICE("切换建议", "输入两个和弦或已验证的切换指标"),
    MOOD_PROGRESSION("情绪生成", "描述情绪，生成经本地验证的进行"),
}

data class AiSettingsUi(
    val enabled: Boolean = false,
    val serviceName: String = "OpenAI Compatible",
    val baseUrl: String = "",
    val model: String = "",
    val temperature: Double = 0.4,
    val timeoutSeconds: Int = 30,
    val hasApiKey: Boolean = false,
)

data class AiValidatedItemUi(
    val title: String,
    val detail: String,
)

enum class AiAcceptKind { NONE, PROGRESSION, PRACTICE }

data class AiResultUi(
    val id: String = UUID.randomUUID().toString(),
    val task: AiTaskUi,
    val title: String,
    val aiExplanation: String,
    val localValidation: String,
    val items: List<AiValidatedItemUi> = emptyList(),
    val rejected: List<String> = emptyList(),
    val acceptKind: AiAcceptKind = AiAcceptKind.NONE,
    val progressionSeed: String = "",
)

data class AiFailureUi(
    val type: String,
    val message: String,
    val statusCode: Int = 0,
)

sealed interface AiAcceptResult {
    data class OpenProgression(val seed: String) : AiAcceptResult
    data class OpenPractice(val title: String) : AiAcceptResult
    data object None : AiAcceptResult
}

class AiGatewayException(val failure: AiFailureUi) : RuntimeException(failure.message)

interface AiGateway {
    fun settings(): AiSettingsUi
    fun isConfigured(): Boolean
    fun saveSettings(settings: AiSettingsUi, newApiKey: String?)
    fun clearSettings()
    fun clearCache()
    suspend fun testConnection(): String
    suspend fun submit(task: AiTaskUi, input: String, contextSymbol: String = ""): AiResultUi
    fun cancel()
    fun accept(result: AiResultUi): AiAcceptResult
}

class DefaultAiGateway(
    private val service: AiService,
    private val settingsStore: AiSettingsStore,
    private val validator: AiResultValidator,
    private val repository: ChordRepository,
    private val resultCache: AiResultCache,
    private val practiceDraftStore: PracticePlanDraftStore,
) : AiGateway {
    private val practiceResults = linkedMapOf<String, AiPracticePlanResult.Day>()

    override fun settings(): AiSettingsUi {
        val value = settingsStore.load()
        return AiSettingsUi(
            value.enabled,
            value.serviceName,
            value.baseUrl,
            value.model,
            value.temperature,
            value.timeoutSeconds,
            settingsStore.hasApiKey(),
        )
    }

    override fun isConfigured(): Boolean = service.isConfigured()

    override fun saveSettings(settings: AiSettingsUi, newApiKey: String?) {
        if (settings.enabled) {
            val urlError = OpenAiCompatibleProvider.validateBaseUrl(settings.baseUrl)
            require(urlError == null) { urlError ?: "Base URL 无效" }
            require(settings.model.isNotBlank()) { "启用 AI 前请填写模型名称。" }
            require(!newApiKey.isNullOrBlank() || settingsStore.hasApiKey()) { "启用 AI 前请填写 API Key。" }
        }
        settingsStore.save(
            AiSettings(
                settings.enabled,
                settings.serviceName,
                settings.baseUrl,
                settings.model,
                settings.temperature,
                settings.timeoutSeconds,
            ),
            newApiKey?.takeIf { it.isNotBlank() },
        )
    }

    override fun clearSettings() {
        service.cancelActive()
        settingsStore.clear()
    }

    override fun clearCache() = resultCache.clear()

    override suspend fun testConnection(): String = suspendCancellableCoroutine { continuation ->
        service.testConnection(object : AiService.ResultCallback<String> {
            override fun onSuccess(result: String, rawExplanationJson: String) {
                if (continuation.isActive) continuation.resume(result)
            }

            override fun onError(error: AiError) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error.toException()))
            }
        })
        continuation.invokeOnCancellation { service.cancelActive() }
    }

    override suspend fun submit(task: AiTaskUi, input: String, contextSymbol: String): AiResultUi {
        val settings = service.settings()
        return when (task) {
            AiTaskUi.RECOMMEND_CHORDS -> execute(
                AiPromptFactory.chordRecommendation(input, settings),
                { validator.validateChordRecommendations(it, false) },
            ) { result: AiChordRecommendationResult ->
                AiResultUi(
                    task = task,
                    title = "和弦建议",
                    aiExplanation = "AI 根据输入给出候选；正式和弦与按法均来自本地库。",
                    localValidation = "已通过本地名称、组成音与按法校验。",
                    items = result.candidates.map { AiValidatedItemUi(it.symbol, it.reason) },
                    rejected = result.rejectedSymbols,
                )
            }
            AiTaskUi.GENERATE_PROGRESSION, AiTaskUi.MOOD_PROGRESSION -> execute(
                AiPromptFactory.progression(
                    if (task == AiTaskUi.MOOD_PROGRESSION) "情绪目标：$input" else input,
                    settings,
                ),
                validator::validateProgression,
            ) { result: AiProgressionResult -> progressionResult(task, result) }
            AiTaskUi.EXPLAIN_CHORD -> {
                val symbol = contextSymbol.ifBlank { input }.trim()
                val lookup = repository.find(symbol)
                require(lookup.recognized && lookup.chord != null) { lookup.message ?: "无法识别和弦。" }
                execute(
                    AiPromptFactory.explainChord(lookup.chord, "本地用户", settings),
                    { validator.validateExplanation(it, AiPromptFactory.TASK_CHORD_EXPLANATION, 1_200) },
                ) { explanation: String ->
                    AiResultUi(
                        task = task,
                        title = "${lookup.chord.symbol} 解释",
                        aiExplanation = explanation,
                        localValidation = "组成音 ${lookup.chord.notes.joinToString(" · ")} 与音程 ${lookup.chord.intervals.joinToString(" · ")} 由本地引擎提供。",
                    )
                }
            }
            AiTaskUi.OPTIMIZE_PROGRESSION -> execute(
                AiPromptFactory.progressionOptimization(input, settings),
                validator::validateProgressionOptimization,
            ) { result: AiProgressionOptimizationResult ->
                AiResultUi(
                    task = task,
                    title = "进行优化建议",
                    aiExplanation = result.explanation,
                    localValidation = result.localAnalysis.ifBlank { "全部和弦已由本地仓库重新验证。" },
                    items = result.proposedChords.map { AiValidatedItemUi(it.symbol, "${it.beats} 拍") },
                    acceptKind = AiAcceptKind.PROGRESSION,
                    progressionSeed = result.proposedChords.joinToString(" ") { it.symbol },
                )
            }
            AiTaskUi.PRACTICE_PLAN -> execute(
                AiPromptFactory.practicePlan(input, settings),
                validator::validatePracticePlan,
            ) { result: AiPracticePlanResult ->
                val id = UUID.randomUUID().toString()
                result.days.firstOrNull()?.let { practiceResults[id] = it }
                AiResultUi(
                    id = id,
                    task = task,
                    title = "练习计划",
                    aiExplanation = "AI 仅安排已在本地验证的和弦；发送前的统计摘要由用户确认。",
                    localValidation = "${result.days.size} 天计划均通过时长、BPM 与本地和弦校验。",
                    items = result.days.map {
                        AiValidatedItemUi(it.title, "${it.durationMinutes} 分钟 · ${it.bpm} BPM · ${it.chords.joinToString(" ") { chord -> chord.symbol }}")
                    },
                    acceptKind = if (result.days.isEmpty()) AiAcceptKind.NONE else AiAcceptKind.PRACTICE,
                )
            }
            AiTaskUi.TRANSITION_ADVICE -> execute(
                AiPromptFactory.transitionExplanation(input, settings),
                { validator.validateExplanation(it, AiPromptFactory.TASK_TRANSITION_EXPLANATION, 1_200) },
            ) { explanation: String ->
                AiResultUi(
                    task = task,
                    title = "切换建议",
                    aiExplanation = explanation,
                    localValidation = "AI 只解释输入的本地切换指标，不生成 frets 或 fingers。",
                )
            }
        }
    }

    override fun cancel() = service.cancelActive()

    override fun accept(result: AiResultUi): AiAcceptResult = when (result.acceptKind) {
        AiAcceptKind.PROGRESSION -> AiAcceptResult.OpenProgression(result.progressionSeed)
        AiAcceptKind.PRACTICE -> {
            val day = practiceResults.remove(result.id) ?: return AiAcceptResult.None
            practiceDraftStore.save(day)
            AiAcceptResult.OpenPractice(day.title)
        }
        AiAcceptKind.NONE -> AiAcceptResult.None
    }

    private fun progressionResult(task: AiTaskUi, result: AiProgressionResult) = AiResultUi(
        task = task,
        title = "${result.key.ifBlank { "未指定调性" }} 进行",
        aiExplanation = result.explanation,
        localValidation = result.localAnalysis.ifBlank {
            "已验证 ${result.chords.size} 个本地和弦；建议 ${result.tempoSuggestion} BPM。"
        },
        items = result.chords.map { AiValidatedItemUi(it.symbol, "${it.beats} 拍") },
        rejected = result.rejectedSymbols,
        acceptKind = AiAcceptKind.PROGRESSION,
        progressionSeed = result.chords.joinToString(" ") { it.symbol },
    )

    private suspend fun <T> execute(
        request: com.k2.music.AiRequest,
        parser: AiService.StructuredParser<T>,
        mapper: (T) -> AiResultUi,
    ): AiResultUi = suspendCancellableCoroutine { continuation ->
        service.executeStructured(request, parser, object : AiService.ResultCallback<T> {
            override fun onSuccess(result: T, rawExplanationJson: String) {
                if (continuation.isActive) continuation.resume(mapper(result))
            }

            override fun onError(error: AiError) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(error.toException()))
            }
        })
        continuation.invokeOnCancellation { service.cancelActive() }
    }

    private fun AiError.toException() = AiGatewayException(
        AiFailureUi(type.name, message, statusCode),
    )
}

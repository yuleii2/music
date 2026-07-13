package com.k2.music.ui.gateway

import android.content.Context
import androidx.core.net.toUri
import com.k2.music.ChordRepository
import com.k2.music.CustomVoicingStore
import com.k2.music.UserChordStore
import com.k2.music.VoicingImageExporter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

enum class ExportFormatUi(val value: String, val label: String) {
    JPG(VoicingImageExporter.FORMAT_JPG, "JPG"),
    PNG(VoicingImageExporter.FORMAT_PNG, "PNG"),
    SVG(VoicingImageExporter.FORMAT_SVG, "SVG"),
}

enum class ExportScopeUi { CURRENT_VOICING, CHORD_ALL, SELECTION, FAVORITES }

data class ExportRequestUi(
    val scope: ExportScopeUi,
    val symbols: List<String> = emptyList(),
    val currentVoicingIndex: Int = 0,
)

data class ExportProgressUi(
    val total: Int,
    val completed: Int,
    val succeeded: Int,
    val failed: Int,
    val firstFileName: String = "",
    val running: Boolean = true,
    val cancelled: Boolean = false,
)

interface ExportGateway {
    suspend fun count(request: ExportRequestUi): Int
    suspend fun export(
        request: ExportRequestUi,
        folderUri: String,
        prefix: String,
        format: ExportFormatUi,
        onProgress: (ExportProgressUi) -> Unit,
    ): ExportProgressUi
}

class DefaultExportGateway(
    context: Context,
    private val repository: ChordRepository,
    private val customVoicingStore: CustomVoicingStore,
    private val userChordStore: UserChordStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ExportGateway {
    private val applicationContext = context.applicationContext

    override suspend fun count(request: ExportRequestUi): Int = withContext(dispatcher) {
        buildItems(request).size
    }

    override suspend fun export(
        request: ExportRequestUi,
        folderUri: String,
        prefix: String,
        format: ExportFormatUi,
        onProgress: (ExportProgressUi) -> Unit,
    ): ExportProgressUi = withContext(dispatcher) {
        val items = buildItems(request)
        require(items.isNotEmpty()) { "当前范围没有可导出的指法。" }
        val targetUri = folderUri.toUri()
        var succeeded = 0
        var failed = 0
        var firstFile = ""
        var completed = 0
        onProgress(ExportProgressUi(items.size, 0, 0, 0))
        for (item in items) {
            coroutineContext.ensureActive()
            val summary = VoicingImageExporter.export(
                applicationContext,
                targetUri,
                prefix.ifBlank { "chord" },
                format.value,
                listOf(item),
            )
            succeeded += summary.exported
            failed += summary.failed
            if (firstFile.isBlank()) firstFile = summary.fileNames.firstOrNull().orEmpty()
            completed++
            onProgress(
                ExportProgressUi(
                    total = items.size,
                    completed = completed,
                    succeeded = succeeded,
                    failed = failed,
                    firstFileName = firstFile,
                ),
            )
        }
        ExportProgressUi(
            total = items.size,
            completed = completed,
            succeeded = succeeded,
            failed = failed,
            firstFileName = firstFile,
            running = false,
        )
    }

    private fun buildItems(request: ExportRequestUi): List<VoicingImageExporter.ExportItem> {
        val symbols = when (request.scope) {
            ExportScopeUi.FAVORITES -> userChordStore.favorites()
            else -> request.symbols
        }.distinct()
        val result = mutableListOf<VoicingImageExporter.ExportItem>()
        symbols.forEach { symbol ->
            val lookup = repository.find(symbol)
            if (!lookup.recognized || lookup.chord == null) return@forEach
            val voicings = customVoicingStore.mergeWithBuiltIns(lookup.chord.symbol, lookup.chord.voicings)
            val selected = if (request.scope == ExportScopeUi.CURRENT_VOICING) {
                voicings.getOrNull(request.currentVoicingIndex)?.let(::listOf).orEmpty()
            } else {
                voicings
            }
            selected.forEachIndexed { index, voicing ->
                result += VoicingImageExporter.ExportItem(lookup.chord, voicing, index + 1)
            }
        }
        return result
    }
}

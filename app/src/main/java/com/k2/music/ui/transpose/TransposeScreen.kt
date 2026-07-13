package com.k2.music.ui.transpose

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.MusicTheoryUtils
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.components.AdaptiveControlGroup
import com.k2.music.ui.gateway.CapoSuggestionUi
import kotlinx.coroutines.flow.collectLatest

@Composable
fun TransposeRoute(
    services: CoreServices,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onOpenChord: (String) -> Unit,
    onAddProgression: (String) -> Unit,
) {
    val factory = remember(services) {
        MusicViewModelFactory(TransposeViewModel::class) { handle ->
            TransposeViewModel(services.transposeGateway, handle)
        }
    }
    val viewModel: TransposeViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is TransposeEffect.Copy -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("和弦结果", effect.text))
                    snackbarHostState.showSnackbar("已复制结果")
                }
                is TransposeEffect.OpenChord -> onOpenChord(effect.symbol)
                is TransposeEffect.AddProgression -> onAddProgression(effect.progression)
            }
        }
    }
    TransposeScreen(
        state = state,
        onBack = onBack,
        onSegment = viewModel::setSegment,
        onInput = viewModel::setInput,
        onSemitones = viewModel::setSemitones,
        onPreference = viewModel::setPreference,
        onCalculateTranspose = viewModel::calculateTranspose,
        onCopy = viewModel::copyResult,
        onOpen = viewModel::openFirstResult,
        onAddProgression = viewModel::addResultToProgression,
        onCapoMode = viewModel::setCapoMode,
        onCapoFret = viewModel::setCapoFret,
        onActual = viewModel::setActualChords,
        onPreferred = viewModel::setPreferredShapes,
        onShape = viewModel::setShapeInput,
        onCalculateCapo = viewModel::calculateCapo,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransposeScreen(
    state: TransposeUiState,
    onBack: () -> Unit,
    onSegment: (TransposeSegment) -> Unit,
    onInput: (String) -> Unit,
    onSemitones: (Int) -> Unit,
    onPreference: (MusicTheoryUtils.AccidentalPreference) -> Unit,
    onCalculateTranspose: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onAddProgression: () -> Unit,
    onCapoMode: (CapoMode) -> Unit,
    onCapoFret: (Int) -> Unit,
    onActual: (String) -> Unit,
    onPreferred: (String) -> Unit,
    onShape: (String) -> Unit,
    onCalculateCapo: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("transpose_screen"),
        topBar = {
            TopAppBar(
                title = { Text("移调与变调夹") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("segments") {
                AdaptiveControlGroup {
                    TransposeSegment.entries.forEach { segment ->
                        FilterChip(
                            selected = state.segment == segment,
                            onClick = { onSegment(segment) },
                            label = { Text(segment.label) },
                        )
                    }
                }
            }
            if (state.segment == TransposeSegment.TRANSPOSE) {
                item("transpose-form") {
                    TransposeForm(
                        state,
                        onInput,
                        onSemitones,
                        onPreference,
                        onCalculateTranspose,
                        onCopy,
                        onOpen,
                        onAddProgression,
                    )
                }
            } else {
                item("capo-form") {
                    CapoForm(
                        state,
                        onCapoMode,
                        onCapoFret,
                        onPreference,
                        onActual,
                        onPreferred,
                        onShape,
                        onCalculateCapo,
                        onCopy,
                        onOpen,
                    )
                }
                if (state.capoSuggestions.isNotEmpty()) {
                    item("suggestion-title") { Text("匹配结果", style = MaterialTheme.typography.titleLarge) }
                    items(state.capoSuggestions, key = { it.capoFret }) { suggestion ->
                        CapoSuggestionCard(suggestion)
                    }
                }
            }
        }
    }
}

@Composable
private fun TransposeForm(
    state: TransposeUiState,
    onInput: (String) -> Unit,
    onSemitones: (Int) -> Unit,
    onPreference: (MusicTheoryUtils.AccidentalPreference) -> Unit,
    onCalculate: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onAddProgression: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedTextField(
            value = state.input,
            onValueChange = onInput,
            modifier = Modifier.fillMaxWidth().testTag("transpose_input"),
            label = { Text("和弦或和弦进行") },
            placeholder = { Text("C G Am F 或 Dm7 G7 Cmaj7") },
            minLines = 2,
        )
        ValueSlider("半音", state.semitones, -11, 11, onSemitones)
        AccidentalPreferenceRow(state.preference, onPreference)
        Button(
            onClick = onCalculate,
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("transpose_calculate"),
        ) {
            if (state.calculating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("移调", modifier = Modifier.padding(start = if (state.calculating) 8.dp else 0.dp))
        }
        state.error?.let { InlineMessage(it, isError = true) }
        if (state.result.isNotBlank()) {
            ResultCard("移调结果", state.result, onCopy, onOpen, onAddProgression)
        }
    }
}

@Composable
private fun CapoForm(
    state: TransposeUiState,
    onMode: (CapoMode) -> Unit,
    onCapoFret: (Int) -> Unit,
    onPreference: (MusicTheoryUtils.AccidentalPreference) -> Unit,
    onActual: (String) -> Unit,
    onPreferred: (String) -> Unit,
    onShape: (String) -> Unit,
    onCalculate: () -> Unit,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        AdaptiveControlGroup {
            CapoMode.entries.forEach { mode ->
                FilterChip(selected = state.capoMode == mode, onClick = { onMode(mode) }, label = { Text(mode.label) })
            }
        }
        if (state.capoMode == CapoMode.FIND_POSITION) {
            OutlinedTextField(
                state.actualChords,
                onActual,
                Modifier.fillMaxWidth(),
                label = { Text("目标实际和弦") },
                placeholder = { Text("例如 D A Bm G") },
                minLines = 2,
            )
            OutlinedTextField(
                state.preferredShapes,
                onPreferred,
                Modifier.fillMaxWidth(),
                label = { Text("希望使用的指法") },
                placeholder = { Text("例如 C G Am F") },
                minLines = 2,
            )
        } else {
            OutlinedTextField(
                state.shapeInput,
                onShape,
                Modifier.fillMaxWidth(),
                label = { Text("已知指法") },
                placeholder = { Text("例如 C G/B Am") },
                minLines = 2,
            )
            ValueSlider("变调夹品位", state.capoFret, 0, 12, onCapoFret)
            AccidentalPreferenceRow(state.preference, onPreference)
        }
        Button(onClick = onCalculate, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("计算") }
        state.capoError?.let { InlineMessage(it, isError = true) }
        if (state.capoResult.isNotBlank()) ResultCard("实际声音", state.capoResult, onCopy, onOpen, null)
    }
}

@Composable
private fun ValueSlider(label: String, value: Int, minimum: Int, maximum: Int, onValue: (Int) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("$label：${if (value > 0 && minimum < 0) "+$value" else value}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { onValue(value - 1) }, enabled = value > minimum) {
                Icon(Icons.Rounded.Remove, contentDescription = "$label 减一")
            }
            IconButton(onClick = { onValue(value + 1) }, enabled = value < maximum) {
                Icon(Icons.Rounded.Add, contentDescription = "$label 加一")
            }
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt()) },
            valueRange = minimum.toFloat()..maximum.toFloat(),
            steps = (maximum - minimum - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun AccidentalPreferenceRow(
    preference: MusicTheoryUtils.AccidentalPreference,
    onPreference: (MusicTheoryUtils.AccidentalPreference) -> Unit,
) {
    AdaptiveControlGroup {
        listOf(
            MusicTheoryUtils.AccidentalPreference.AUTO to "自动",
            MusicTheoryUtils.AccidentalPreference.SHARPS to "升号",
            MusicTheoryUtils.AccidentalPreference.FLATS to "降号",
        ).forEach { option ->
            FilterChip(
                selected = preference == option.first,
                onClick = { onPreference(option.first) },
                label = { Text(option.second) },
            )
        }
    }
}

@Composable
private fun ResultCard(
    title: String,
    result: String,
    onCopy: () -> Unit,
    onOpen: () -> Unit,
    onAddProgression: (() -> Unit)?,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(result, style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TextButton(onClick = onCopy, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = null)
                    Text("复制")
                }
                TextButton(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null)
                    Text("详情")
                }
                onAddProgression?.let { action ->
                    TextButton(onClick = action, modifier = Modifier.weight(1f)) { Text("加入进行") }
                }
            }
        }
    }
}

@Composable
private fun CapoSuggestionCard(suggestion: CapoSuggestionUi) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("形状", style = MaterialTheme.typography.labelMedium)
                Text(suggestion.shapes.joinToString(" "), style = MaterialTheme.typography.bodyLarge)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Capo", style = MaterialTheme.typography.labelMedium)
                Text("${suggestion.capoFret} 品", style = MaterialTheme.typography.titleLarge)
            }
            Column(Modifier.weight(1f)) {
                Text("实际声音", style = MaterialTheme.typography.labelMedium)
                Text(suggestion.soundingChords.joinToString(" "), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

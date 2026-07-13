package com.k2.music.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.k2.music.ui.CoreServices
import com.k2.music.ui.MusicViewModelFactory
import com.k2.music.ui.components.AdaptiveStat
import com.k2.music.ui.components.AdaptiveStatGrid
import com.k2.music.ui.components.InlineMessage
import com.k2.music.ui.gateway.PracticeGateway
import com.k2.music.ui.gateway.PracticeSummaryUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PracticeProgressUiState(
    val loading: Boolean = true,
    val summary: PracticeSummaryUi = PracticeSummaryUi(),
    val error: String? = null,
)

class PracticeProgressViewModel(private val gateway: PracticeGateway) : ViewModel() {
    private val _state = MutableStateFlow(PracticeProgressUiState())
    val state = _state.asStateFlow()
    init {
        viewModelScope.launch {
            runCatching { gateway.summary() }
                .onSuccess { _state.value = PracticeProgressUiState(false, it) }
                .onFailure { _state.value = PracticeProgressUiState(false, error = it.message ?: "无法读取练习进步。") }
        }
    }
}

@Composable
fun PracticeProgressRoute(services: CoreServices, onBack: () -> Unit) {
    val factory = remember(services) {
        MusicViewModelFactory(PracticeProgressViewModel::class) { PracticeProgressViewModel(services.practiceGateway) }
    }
    val viewModel: PracticeProgressViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()
    PracticeProgressScreen(state, onBack)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun PracticeProgressScreen(state: PracticeProgressUiState, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("练习进步") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> Row(Modifier.fillMaxSize().padding(padding), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator()
            }
            state.error != null -> InlineMessage(state.error, isError = true, modifier = Modifier.padding(padding).padding(20.dp))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                item("stats") {
                    AdaptiveStatGrid(
                        listOf(
                            AdaptiveStat("今日练习", "${state.summary.todaySeconds / 60} 分钟"),
                            AdaptiveStat("近 7 天练习", "${state.summary.sevenDaySessions} 次"),
                            AdaptiveStat("近 7 天尝试", state.summary.sevenDayAttempts.toString()),
                            AdaptiveStat("近 7 天成功率", state.summary.sevenDaySuccessRate?.let { "%.0f%%".format(it * 100) } ?: "数据不足"),
                            AdaptiveStat("最熟练切换", state.summary.strongestTransition?.key?.label ?: "数据不足"),
                            AdaptiveStat("最需复习切换", state.summary.weakestTransition?.key?.label ?: "数据不足"),
                            AdaptiveStat("最高稳定速度", state.summary.highestStableBpm?.let { "$it BPM" } ?: "数据不足"),
                        ),
                    )
                }
                item("trend-title") { Text("最近 7 天每日练习时长", style = MaterialTheme.typography.titleLarge) }
                if (state.summary.dailyPracticeSeconds.all { it == 0L }) {
                    item("empty") { InlineMessage("完成并保存一次练习后，这里会显示真实的每日趋势。") }
                } else {
                    val maxSeconds = state.summary.dailyPracticeSeconds.maxOrNull()?.coerceAtLeast(1L) ?: 1L
                    state.summary.dailyPracticeSeconds.forEachIndexed { index, seconds ->
                        item("day-$index") {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(Modifier.fillMaxWidth()) {
                                    Text(if (index == 6) "今天" else "${6 - index} 天前", modifier = Modifier.weight(1f))
                                    Text("${seconds / 60} 分 ${seconds % 60} 秒")
                                }
                                LinearProgressIndicator(
                                    progress = { seconds.toFloat() / maxSeconds },
                                    modifier = Modifier.fillMaxWidth().height(10.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

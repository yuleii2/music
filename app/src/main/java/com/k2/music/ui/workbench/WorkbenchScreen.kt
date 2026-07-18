package com.k2.music.ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.k2.music.ui.components.StudioGroup
import com.k2.music.ui.components.StudioListItem
import com.k2.music.ui.components.StudioPageHeader
import com.k2.music.ui.components.StudioSectionHeader

private data class ToolDefinition(
    val id: String,
    val title: String,
    val description: String,
    val status: String,
    val icon: ImageVector,
)

@Composable
fun WorkbenchScreen(
    recentToolId: String?,
    onToolUsed: (String) -> Unit,
    onRecognition: () -> Unit,
    onTranspose: () -> Unit,
    onProgressions: () -> Unit,
    onMetronome: () -> Unit,
    onAiAssistant: () -> Unit,
) {
    val tools = listOf(
        ToolDefinition("recognition", "反向识别", "从指板位置或音符集合识别和弦。", "离线", Icons.Rounded.GraphicEq),
        ToolDefinition("transpose", "移调与变调夹", "处理单个和弦、指定低音或整段进行。", "−11…+11", Icons.Rounded.SwapHoriz),
        ToolDefinition("progressions", "和弦进行", "编排步骤、按法、节拍与本地播放。", "本地保存", Icons.Rounded.Piano),
        ToolDefinition("metronome", "节拍器", "使用绝对时间锚点保持稳定节拍。", "2/4–6/8", Icons.Rounded.MusicNote),
        ToolDefinition("ai", "AI 助手", "按需联网的建议层；结果仍由本地核心校验。", "可选", Icons.Rounded.AutoAwesome),
    )
    val rawActions = mapOf(
        "recognition" to onRecognition,
        "transpose" to onTranspose,
        "progressions" to onProgressions,
        "metronome" to onMetronome,
        "ai" to onAiAssistant,
    )
    val actions = rawActions.mapValues { (id, action) ->
        {
            onToolUsed(id)
            action()
        }
    }
    val recentTool = tools.firstOrNull { it.id == recentToolId }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = 820.dp).fillMaxSize().testTag("workbench_screen"),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("header") {
                StudioPageHeader(
                    title = "工具",
                    subtitle = "五个独立模块，默认离线运行。",
                )
            }
            recentTool?.let { recent ->
                item("recent_label") { StudioSectionHeader("最近使用") }
                item("recent_${recent.id}") {
                    ToolGroup(listOf(recent), actions)
                }
            }
            item("analysis_label") { StudioSectionHeader("分析") }
            item("analysis") { ToolGroup(tools.take(2), actions) }
            item("arrangement_label") { StudioSectionHeader("编排与时间") }
            item("arrangement") { ToolGroup(tools.slice(2..3), actions) }
            item("assistant_label") { StudioSectionHeader("可选服务", "默认不联网") }
            item("assistant") { ToolGroup(listOf(tools.last()), actions) }
        }
    }
}

@Composable
private fun ToolGroup(
    tools: List<ToolDefinition>,
    actions: Map<String, () -> Unit>,
) {
    StudioGroup {
        tools.forEachIndexed { index, tool ->
            StudioListItem(
                title = tool.title,
                subtitle = tool.description,
                icon = tool.icon,
                meta = tool.status,
                onClick = actions.getValue(tool.id),
                showDivider = index != tools.lastIndex,
                testTag = "tool_${tool.id}",
            )
        }
    }
}

package com.k2.music.ui.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Piano
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

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
        ToolDefinition("recognition", "反向识别", "在六弦指板上点按，或输入音符来寻找和弦。", "离线 · 最多 5 个候选", Icons.Rounded.GraphicEq),
        ToolDefinition("transpose", "移调与变调夹", "移动单个和弦或整段进行，并同步处理指定低音。", "离线 · -11…+11", Icons.Rounded.SwapHoriz),
        ToolDefinition("progressions", "和弦进行", "编排步骤、推荐按法、设置节拍并播放。", "本地保存", Icons.Rounded.Piano),
        ToolDefinition("metronome", "节拍器", "用绝对时间锚点保持稳定节拍。", "2/4 · 3/4 · 4/4 · 6/8", Icons.Rounded.MusicNote),
        ToolDefinition("ai", "AI 助手", "可选的建议层；所有结果继续由本地核心验证。", "默认关闭", Icons.Rounded.AutoAwesome),
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
    val remainingTools = tools.filterNot { it.id == recentTool?.id }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(260.dp),
        modifier = Modifier.fillMaxSize().testTag("workbench_screen"),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("工具", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "所有音乐工具都能独立于 AI 使用。",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        recentTool?.let { recent ->
            item(key = "recent_label", span = { GridItemSpan(maxLineSpan) }) {
                Text("最近使用", style = MaterialTheme.typography.titleMedium)
            }
            item(key = "recent_${recent.id}", span = { GridItemSpan(maxLineSpan) }) {
                ToolCard(tool = recent, onClick = actions.getValue(recent.id))
            }
        }
        item(key = "all_label", span = { GridItemSpan(maxLineSpan) }) {
            Text("全部工具", style = MaterialTheme.typography.titleMedium)
        }
        items(remainingTools, key = { it.id }, contentType = { "tool" }) { tool ->
            ToolCard(tool = tool, onClick = actions.getValue(tool.id))
        }
    }
}

@Composable
private fun ToolCard(tool: ToolDefinition, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("tool_${tool.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = MaterialTheme.shapes.medium,
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    tool.icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(tool.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Rounded.TrendingFlat, contentDescription = "打开${tool.title}")
            }
            Text(tool.description, style = MaterialTheme.typography.bodyLarge)
            Text(
                tool.status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

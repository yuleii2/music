package com.k2.music.ui.recognition

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.k2.music.ui.components.calculateFretboardGeometry
import kotlin.math.roundToInt

@Composable
fun EditableFretboardCanvas(
    frets: List<Int>,
    startFret: Int,
    tool: FretInputTool,
    onTap: (stringIndex: Int, absoluteFret: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outline
    val markerColor = MaterialTheme.colorScheme.primary
    val markerText = MaterialTheme.colorScheme.onPrimary
    val neutral = MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val radius = with(density) { 13.dp.toPx() }
    val lineWidth = with(density) { 1.25.dp.toPx() }
    val description = frets.mapIndexed { index, fret ->
        val state = when (fret) {
            UNSET_FRET -> "未设置"
            -1 -> "闷弦 X"
            0 -> "空弦 O"
            else -> "$fret 品"
        }
        "${6 - index} 弦 $state"
    }.joinToString("；")
    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = "可编辑吉他指板，显示 $startFret–${startFret + 4} 品，当前工具 ${tool.label}。$description"
            }
            .pointerInput(startFret, tool, onTap) {
                detectTapGestures { offset ->
                    val geometry = calculateFretboardGeometry(size.width.toFloat(), size.height.toFloat(), startFret, 5)
                    val string = ((offset.x - geometry.left) / geometry.stringSpacing).roundToInt().coerceIn(0, 5)
                    val fretIndex = ((offset.y - geometry.boardTop) / geometry.fretSpacing).toInt().coerceIn(0, 4)
                    onTap(string, startFret + fretIndex)
                }
            },
    ) {
        val geometry = calculateFretboardGeometry(size.width, size.height, startFret, 5)
        for (string in 0..5) {
            drawLine(lineColor, Offset(geometry.stringX(string), geometry.boardTop), Offset(geometry.stringX(string), geometry.boardBottom), lineWidth)
        }
        for (fret in 0..5) {
            drawLine(lineColor, Offset(geometry.left, geometry.fretLineY(fret)), Offset(geometry.right, geometry.fretLineY(fret)), lineWidth)
        }
        frets.take(6).forEachIndexed { index, fret ->
            val x = geometry.stringX(index)
            when {
                fret > 0 -> {
                    val visible = fret.coerceIn(startFret, startFret + 4)
                    drawCircle(markerColor, radius, Offset(x, geometry.fretCenterY(visible.toFloat())))
                    drawCircle(markerText, radius * 0.18f, Offset(x, geometry.fretCenterY(visible.toFloat())))
                }
                fret == 0 -> drawCircle(neutral, radius * 0.58f, Offset(x, geometry.markerY), style = Stroke(lineWidth * 1.6f))
                fret == -1 -> {
                    val r = radius * 0.55f
                    drawLine(neutral, Offset(x - r, geometry.markerY - r), Offset(x + r, geometry.markerY + r), lineWidth * 1.8f, StrokeCap.Round)
                    drawLine(neutral, Offset(x + r, geometry.markerY - r), Offset(x - r, geometry.markerY + r), lineWidth * 1.8f, StrokeCap.Round)
                }
            }
        }
    }
}

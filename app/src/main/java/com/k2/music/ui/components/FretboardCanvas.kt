package com.k2.music.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.model.VoicingUiModel
import com.k2.music.ui.model.fretboardDescription
import com.k2.music.ui.theme.LocalMusicMotion

@Composable
fun FretboardCanvas(
    chord: ChordUiModel,
    voicing: VoicingUiModel,
    modifier: Modifier = Modifier,
    showFingerNumbers: Boolean = true,
) {
    val motion = LocalMusicMotion.current
    val lineColor = androidx.compose.material3.MaterialTheme.colorScheme.outline
    val nutColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurface
    val markerColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val markerTextColor = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary
    val openMutedColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
    val density = LocalDensity.current
    val markerRadius = with(density) { 13.dp.toPx() }
    val lineWidth = with(density) { 1.25.dp.toPx() }
    val nutWidth = with(density) { 4.dp.toPx() }
    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }
    textPaint.color = markerTextColor.toArgbCompat()
    textPaint.textSize = markerRadius * 0.95f

    val animatedFrets = voicing.frets.mapIndexed { index, target ->
        val state by animateFloatAsState(
            targetValue = target.coerceAtLeast(voicing.startFret).toFloat(),
            animationSpec = tween(motion.emphasized),
            label = "fret-$index",
        )
        state
    }
    val pressedAlpha = voicing.frets.mapIndexed { index, target ->
        val state by animateFloatAsState(
            targetValue = if (target > 0) 1f else 0f,
            animationSpec = tween(motion.quick),
            label = "pressed-alpha-$index",
        )
        state
    }
    val openAlpha = voicing.frets.mapIndexed { index, target ->
        val state by animateFloatAsState(
            targetValue = if (target == 0) 1f else 0f,
            animationSpec = tween(motion.quick),
            label = "open-alpha-$index",
        )
        state
    }
    val mutedAlpha = voicing.frets.mapIndexed { index, target ->
        val state by animateFloatAsState(
            targetValue = if (target < 0) 1f else 0f,
            animationSpec = tween(motion.quick),
            label = "muted-alpha-$index",
        )
        state
    }
    val barre = remember(voicing.frets, voicing.fingers, voicing.barre) {
        if (voicing.barre) detectPrimaryBarre(voicing.frets, voicing.fingers) else null
    }
    val barreAlpha by animateFloatAsState(
        targetValue = if (barre != null) 1f else 0f,
        animationSpec = tween(motion.quick),
        label = "barre-alpha",
    )
    val animatedBarreFret by animateFloatAsState(
        targetValue = (barre?.fret ?: voicing.startFret).toFloat(),
        animationSpec = tween(motion.emphasized),
        label = "barre-fret",
    )

    Box(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = fretboardDescription(chord, voicing)
        },
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val geometry = calculateFretboardGeometry(
                        size.width,
                        size.height,
                        voicing.startFret,
                        voicing.displayFrets,
                    )
                    onDrawBehind {
                        for (stringIndex in 0..5) {
                            drawLine(
                                color = lineColor,
                                start = Offset(geometry.stringX(stringIndex), geometry.boardTop),
                                end = Offset(geometry.stringX(stringIndex), geometry.boardBottom),
                                strokeWidth = lineWidth,
                            )
                        }
                        for (fretIndex in 0..geometry.displayFrets) {
                            drawLine(
                                color = if (fretIndex == 0 && voicing.startFret == 1) nutColor else lineColor,
                                start = Offset(geometry.left, geometry.fretLineY(fretIndex)),
                                end = Offset(geometry.right, geometry.fretLineY(fretIndex)),
                                strokeWidth = if (fretIndex == 0 && voicing.startFret == 1) nutWidth else lineWidth,
                            )
                        }
                    }
                },
        ) { }
        Canvas(Modifier.fillMaxSize()) {
            val geometry = calculateFretboardGeometry(
                size.width,
                size.height,
                voicing.startFret,
                voicing.displayFrets,
            )
            barre?.let { value ->
                val x1 = geometry.stringX(value.firstStringIndex) - markerRadius
                val x2 = geometry.stringX(value.lastStringIndex) + markerRadius
                val y = geometry.fretCenterY(animatedBarreFret) - markerRadius
                drawRoundRect(
                    color = markerColor.copy(alpha = barreAlpha),
                    topLeft = Offset(x1, y),
                    size = Size(x2 - x1, markerRadius * 2f),
                    cornerRadius = CornerRadius(markerRadius, markerRadius),
                )
            }
            for (index in 0..5) {
                val x = geometry.stringX(index)
                if (pressedAlpha[index] > 0f) {
                    val y = geometry.fretCenterY(animatedFrets[index])
                    drawCircle(markerColor.copy(alpha = pressedAlpha[index]), markerRadius, Offset(x, y))
                    val finger = voicing.fingers.getOrNull(index) ?: 0
                    if (showFingerNumbers && finger > 0) {
                        drawContext.canvas.nativeCanvas.drawText(
                            finger.toString(),
                            x,
                            y + textPaint.textSize * 0.34f,
                            textPaint,
                        )
                    }
                }
                if (openAlpha[index] > 0f) {
                    drawCircle(
                        color = openMutedColor.copy(alpha = openAlpha[index]),
                        radius = markerRadius * 0.58f,
                        center = Offset(x, geometry.markerY),
                        style = Stroke(width = lineWidth * 1.6f),
                    )
                }
                if (mutedAlpha[index] > 0f) {
                    val radius = markerRadius * 0.55f
                    val color = openMutedColor.copy(alpha = mutedAlpha[index])
                    drawLine(
                        color,
                        Offset(x - radius, geometry.markerY - radius),
                        Offset(x + radius, geometry.markerY + radius),
                        lineWidth * 1.8f,
                        StrokeCap.Round,
                    )
                    drawLine(
                        color,
                        Offset(x + radius, geometry.markerY - radius),
                        Offset(x - radius, geometry.markerY + radius),
                        lineWidth * 1.8f,
                        StrokeCap.Round,
                    )
                }
            }
        }
    }
}

private fun Color.toArgbCompat(): Int =
    ((alpha * 255).toInt() shl 24) or
        ((red * 255).toInt() shl 16) or
        ((green * 255).toInt() shl 8) or
        (blue * 255).toInt()

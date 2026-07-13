package com.k2.music.ui.components

import kotlin.math.max

data class FretboardGeometry(
    val width: Float,
    val height: Float,
    val left: Float,
    val right: Float,
    val markerY: Float,
    val boardTop: Float,
    val boardBottom: Float,
    val startFret: Int,
    val displayFrets: Int,
) {
    val stringSpacing: Float get() = (right - left) / 5f
    val fretSpacing: Float get() = (boardBottom - boardTop) / displayFrets

    fun stringX(index: Int): Float = left + stringSpacing * index.coerceIn(0, 5)

    fun fretLineY(index: Int): Float = boardTop + fretSpacing * index.coerceIn(0, displayFrets)

    fun fretCenterY(fret: Float): Float {
        val relative = fret - startFret
        return boardTop + (relative + 0.5f) * fretSpacing
    }
}

fun calculateFretboardGeometry(
    width: Float,
    height: Float,
    startFret: Int,
    displayFrets: Int,
): FretboardGeometry {
    val safeWidth = max(1f, width)
    val safeHeight = max(1f, height)
    val horizontalPadding = safeWidth * 0.09f
    val markerY = safeHeight * 0.075f
    val boardTop = safeHeight * 0.16f
    val boardBottom = safeHeight * 0.92f
    return FretboardGeometry(
        width = safeWidth,
        height = safeHeight,
        left = horizontalPadding,
        right = safeWidth - horizontalPadding,
        markerY = markerY,
        boardTop = boardTop,
        boardBottom = boardBottom,
        startFret = max(1, startFret),
        displayFrets = max(1, displayFrets),
    )
}

data class BarreGeometry(
    val fret: Int,
    val firstStringIndex: Int,
    val lastStringIndex: Int,
)

fun detectPrimaryBarre(frets: List<Int>, fingers: List<Int>): BarreGeometry? {
    val candidates = mutableListOf<BarreGeometry>()
    for (finger in 1..4) {
        val positions = frets.indices.filter { index ->
            frets[index] > 0 && fingers.getOrNull(index) == finger
        }
        if (positions.size < 2) continue
        val grouped = positions.groupBy { frets[it] }
        grouped.forEach { (fret, indices) ->
            if (indices.size >= 2) {
                candidates += BarreGeometry(fret, indices.min(), indices.max())
            }
        }
    }
    return candidates.maxByOrNull { it.lastStringIndex - it.firstStringIndex }
}

package com.k2.music.ui.components

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

data class AdaptiveStat(val label: String, val value: String, val supporting: String? = null)

/** Reflows statistics to 3/2/1 columns without shrinking typography at large font scales. */
@Composable
fun AdaptiveStatGrid(
    stats: List<AdaptiveStat>,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val fontScale = LocalDensity.current.fontScale
        val columns = when {
            fontScale >= 1.8f || maxWidth < 300.dp -> 1
            fontScale >= 1.25f || maxWidth < 620.dp -> 2
            else -> 3
        }
        val rows = stats.chunked(columns)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = MaterialTheme.shapes.small,
        ) {
            Column {
                rows.forEachIndexed { rowIndex, rowStats ->
                    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                        rowStats.forEachIndexed { index, stat ->
                            Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp)) {
                                Text(stat.value, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stat.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                stat.supporting?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (index != columns - 1) {
                                VerticalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                        repeat(columns - rowStats.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                    if (rowIndex != rows.lastIndex) {
                        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

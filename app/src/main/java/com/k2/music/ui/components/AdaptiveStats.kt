package com.k2.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
            fontScale >= 1.8f || maxWidth < 360.dp -> 1
            fontScale >= 1.25f || maxWidth < 620.dp -> 2
            else -> 3
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            stats.chunked(columns).forEach { rowStats ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowStats.forEach { stat ->
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        ) {
                            Column(
                                Modifier.fillMaxWidth().padding(14.dp),
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(stat.value, style = MaterialTheme.typography.titleLarge)
                                Text(stat.label, style = MaterialTheme.typography.labelMedium)
                                stat.supporting?.let {
                                    Text(it, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    repeat(columns - rowStats.size) {
                        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

package com.k2.music.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k2.music.ui.model.ChordUiModel
import com.k2.music.ui.navigation.sharedChordBounds

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChordCard(
    chord: ChordUiModel,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onLongClick: (() -> Unit)? = null,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = modifier
            .sharedChordBounds(chord.symbol, "container")
            .semantics {
                contentDescription = buildString {
                    append(chord.symbol)
                    append("，")
                    append(chord.chineseName)
                    append("，组成音 ")
                    append(chord.notes.joinToString("、"))
                    append(if (chord.favorite) "，已收藏" else "，未收藏")
                    if (selected) append("，已选择")
                }
            }
            .combinedClickable(
                role = Role.Button,
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        colors = CardDefaults.cardColors(containerColor = container),
        border = CardDefaults.outlinedCardBorder(),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        chord.symbol,
                        modifier = Modifier.sharedChordBounds(chord.symbol, "title"),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        chord.quality,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (selected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = "已选择",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp).padding(10.dp),
                    )
                } else {
                    IconButton(onClick = onFavoriteClick, modifier = Modifier.size(48.dp)) {
                        Icon(
                            if (chord.favorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            contentDescription = if (chord.favorite) {
                                "取消收藏 ${chord.symbol}"
                            } else {
                                "收藏 ${chord.symbol}"
                            },
                            tint = if (chord.favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Text(
                chord.notes.joinToString(" · "),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            chord.previewVoicing?.let { voicing ->
                FretboardCanvas(
                    chord = chord,
                    voicing = voicing,
                    modifier = Modifier
                        .sharedChordBounds(chord.symbol, "fretboard")
                        .fillMaxWidth()
                        .height(112.dp),
                    showFingerNumbers = false,
                )
            } ?: Box(
                modifier = Modifier.fillMaxWidth().height(112.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "理论和弦\n暂无收录指法",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChordTag(chord.difficultyLabel)
                chord.previewVoicing?.let { voicing ->
                    when {
                        voicing.simplified -> ChordTag("简化")
                        voicing.barre -> ChordTag("横按")
                        voicing.isOpen -> ChordTag("开放")
                    }
                }
            }
        }
    }
}

@Composable
fun ChordTag(label: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium)
    }
}

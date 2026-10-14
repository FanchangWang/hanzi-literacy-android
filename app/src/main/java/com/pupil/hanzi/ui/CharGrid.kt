package com.pupil.hanzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pupil.hanzi.data.StrokeData
import com.pupil.hanzi.stroke.StrokeGeometry
import com.pupil.hanzi.stroke.StrokeStyle
import com.pupil.hanzi.stroke.drawCharacter
import java.util.IdentityHashMap

/** Characters laid out in a wrapping grid, one card each. */
@Composable
fun CharGrid(
    cells: List<CharCell>,
    showPinyin: Boolean,
    animate: Boolean,
    speakEnabled: Boolean,
    onSpeak: (Int) -> Unit,
    onOpenDetail: (Int) -> Unit,
    onSelectReading: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 6,
) {
    // Parsing SVG paths is not free; cache per StrokeData instance.
    val pathCache = remember { IdentityHashMap<StrokeData, List<android.graphics.Path>>() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        itemsIndexed(cells) { index, cell ->
            CharCard(
                cell = cell,
                showPinyin = showPinyin,
                animate = animate,
                speakEnabled = speakEnabled,
                paths = cell.strokes?.let { data ->
                    pathCache.getOrPut(data) { StrokeGeometry.parsePaths(data) }
                }.orEmpty(),
                onSpeak = { onSpeak(index) },
                onOpenDetail = { onOpenDetail(index) },
                onSelectReading = { readingIndex -> onSelectReading(index, readingIndex) },
            )
        }
    }
}

@Composable
private fun CharCard(
    cell: CharCell,
    showPinyin: Boolean,
    animate: Boolean,
    speakEnabled: Boolean,
    paths: List<android.graphics.Path>,
    onSpeak: () -> Unit,
    onOpenDetail: () -> Unit,
    onSelectReading: (Int) -> Unit,
) {
    var pickerOpen by remember { mutableStateOf(false) }
    val reading = cell.reading

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showPinyin && reading != null) {
            if (cell.isPolyphone) {
                Box {
                    PolyphonePinyin(
                        reading = reading,
                        fontSize = 16.sp,
                        onClick = { pickerOpen = true },
                    )
                    if (pickerOpen) {
                        ReadingPicker(
                            cell = cell,
                            onSelect = {
                                onSelectReading(it)
                                pickerOpen = false
                            },
                            onDismiss = { pickerOpen = false },
                        )
                    }
                }
            } else {
                PinyinText(reading = reading, fontSize = 16.sp)
            }
            Spacer(Modifier.height(4.dp))
        }

        CharacterBox(
            cell = cell,
            paths = paths,
            animate = animate,
            onClick = onOpenDetail,
        )

        // Keep the slot present when speech is off so rows stay aligned.
        Box(
            modifier = Modifier.size(36.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (speakEnabled && !cell.isPunctuation && reading != null) {
                IconButton(onClick = onSpeak) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "朗读 ${cell.char}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CharacterBox(
    cell: CharCell,
    paths: List<android.graphics.Path>,
    animate: Boolean,
    onClick: () -> Unit,
) {
    val data = cell.strokes
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .clickable(enabled = !cell.isPunctuation, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (data != null && paths.isNotEmpty()) {
            StrokeAnimation(data = data, paths = paths, animate = animate)
        } else {
            // Punctuation, or a character with no stroke data (one of the 2569
            // that have a definition but no drawing).
            Text(
                text = cell.char.toString(),
                fontSize = 44.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
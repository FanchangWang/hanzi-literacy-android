package com.pupil.hanzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pupil.hanzi.data.PinyinReading
import com.pupil.hanzi.data.StrokeData
import com.pupil.hanzi.stroke.StrokeGeometry
import com.pupil.hanzi.stroke.drawCharacter
import com.pupil.hanzi.ui.theme.LocalCanvasColors

/** How many definitions are listed before the user expands the section. */
private const val COLLAPSED_DEFINITIONS = 3

/** Ceiling once expanded, so a common character cannot make the sheet endless. */
private const val MAX_DEFINITIONS = 30

/**
 * Detail for one character: a large mi zi ge beside the readings, then the
 * stroke-by-stroke breakdown and the definitions.
 *
 * Order matters: the breakdown comes before the definitions, because it is the
 * part worth seeing first and a common character can carry hundreds of entries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    detail: DetailState,
    showPinyin: Boolean,
    speakEnabled: Boolean,
    onSelectReading: (Int) -> Unit,
    onPreviewReading: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var animate by remember { mutableStateOf(false) }
    val cell = detail.cell
    val paths = remember(cell.strokes) {
        cell.strokes?.let { StrokeGeometry.parsePaths(it) }.orEmpty()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            // The readings sit beside the grid, so the title carries only the
            // character.
            Text(
                text = cell.char.toString(),
                style = MaterialTheme.typography.headlineMedium,
            )

            Spacer(Modifier.height(12.dp))

            // The large grid and the readings stay side by side, as in the
            // original layout; only the readings themselves wrap when they run
            // past the width of their column.
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(LocalCanvasColors.current.cellBackground),
                ) {
                    val data = cell.strokes
                    if (data != null && paths.isNotEmpty()) {
                        StrokeAnimation(
                            data = data,
                            paths = paths,
                            animate = animate,
                            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showPinyin && cell.reading != null) {
                        if (cell.isPolyphone) {
                            ReadingRow(
                                cell = cell,
                                onSelect = onSelectReading,
                                onPreview = onPreviewReading,
                            )
                        } else {
                            ReadingChip(
                                reading = cell.reading!!,
                                selected = true,
                                showSpeaker = speakEnabled,
                                onClick = { onPreviewReading(0) },
                                onSpeaker = { onPreviewReading(0) },
                            )
                        }
                    }
                    Button(onClick = { animate = !animate }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text(if (animate) "停止动画" else "笔顺动画")
                    }
                }
            }

            val strokes = cell.strokes
            if (strokes != null && paths.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text("笔顺分解", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                StrokeBreakdown(strokes = strokes, paths = paths)
            }

            if (detail.definitions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                // Collapsed by default: a common character carries hundreds of
                // entries and expanded they dominated the sheet.
                var open by remember { mutableStateOf(false) }
                val shown = if (open) {
                    detail.definitions.take(MAX_DEFINITIONS)
                } else {
                    detail.definitions.take(COLLAPSED_DEFINITIONS)
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { open = !open },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "释义（${detail.definitions.size}）",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (open) "收起 ▲" else "展开 ▼",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                shown.forEach { def ->
                    Column(modifier = Modifier.padding(vertical = 3.dp)) {
                        Text(
                            text = def.word,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(text = def.definition, style = MaterialTheme.typography.bodySmall)
                    }
                }
                val hidden = detail.definitions.size - shown.size
                if (hidden > 0) {
                    Text(
                        text = if (open) "…还有 $hidden 条" else "还有 $hidden 条，点上方展开",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The readings of a polyphone, wrapping onto as many rows as the column needs.
 *
 * Only the selected reading carries a background. Tapping a reading plays it
 * so a pupil can hear the difference; the selection is changed by tapping the
 * highlighted one, which keeps a single tap from silently rewriting what the
 * grid shows.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingRow(
    cell: CharCell,
    onSelect: (Int) -> Unit,
    onPreview: (Int) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        cell.readings.forEachIndexed { index, reading ->
            val selected = index == cell.selectedReading
            ReadingChip(
                reading = reading,
                selected = selected,
                showSpeaker = true,
                // Tapping the pinyin always plays it. The selection is only
                // changed by tapping the reading that is already selected, so a
                // single tap never silently rewrites what the grid shows.
                onClick = { onPreview(index); if (selected) onSelect(index) },
                onSpeaker = { onPreview(index) },
            )
        }
    }
}

@Composable
private fun ReadingChip(
    reading: PinyinReading,
    selected: Boolean,
    showSpeaker: Boolean,
    onClick: () -> Unit,
    onSpeaker: () -> Unit,
) {
    Box(
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(6.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PinyinText(
                reading = reading,
                fontSize = 20.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            if (showSpeaker) {
                Spacer(Modifier.size(4.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = "播放 ${reading.display}",
                    modifier = Modifier
                        .size(18.dp)
                        .clickable(onClick = onSpeaker),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * The stroke order, one small mi zi ge per step.
 *
 * Every cell shows the WHOLE character as a faint silhouette, and the first N
 * cells have strokes 1..N filled in, so the sequence reads as the character
 * being written rather than a row of disconnected marks. The radical stays
 * red throughout, as in the grid.
 *
 * The cells wrap onto as many rows as the sheet is wide, matching the
 * original's `flex-wrap` container rather than a fixed column count.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StrokeBreakdown(
    strokes: StrokeData,
    paths: List<android.graphics.Path>,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 9,
    ) {
        // 1..n inclusive: after step i, the first i strokes are drawn.
        for (drawn in 1..paths.size) {
            CumulativeStrokeStep(
                strokes = strokes,
                paths = paths,
                drawnStrokes = drawn,
                modifier = Modifier.size(84.dp),
            )
        }
    }
}

/**
 * The character after [drawnStrokes] of [paths.size] have been written: the
 * whole glyph as a shadow, with the first [drawnStrokes] filled.
 */
@Composable
private fun CumulativeStrokeStep(
    strokes: StrokeData,
    paths: List<android.graphics.Path>,
    drawnStrokes: Int,
    modifier: Modifier = Modifier,
) {
    val canvas = LocalCanvasColors.current
    androidx.compose.foundation.Canvas(modifier) {
        val style = com.pupil.hanzi.stroke.StrokeStyle(
            showGrid = true,
            showOutline = true,
            showRadical = true,
            showGuides = false,
            gridColor = canvas.grid,
            outlineColor = canvas.outline,
            strokeColor = canvas.stroke,
            radicalColor = canvas.radical,
            // The last cell shows a finished character, so progress sits at 1.
            progress = -1f,
        )
        drawCharacter(
            data = strokes,
            paths = paths,
            size = size.minDimension,
            style = style,
            strokesToDraw = drawnStrokes,
        )
    }
}

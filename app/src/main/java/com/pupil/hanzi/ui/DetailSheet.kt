package com.pupil.hanzi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.pupil.hanzi.stroke.drawCharacter
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
import com.pupil.hanzi.data.StrokeData
import com.pupil.hanzi.stroke.StrokeGeometry

/**
 * Detail for one character: a large tian zi ge, every reading, the
 * definitions, and the stroke-by-stroke breakdown.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    detail: DetailState,
    showPinyin: Boolean,
    speakEnabled: Boolean,
    onSelectReading: (Int) -> Unit,
    onSpeak: () -> Unit,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (cell.isPolyphone) {
                        "${cell.char}（多音字：${cell.readings.joinToString("、") { it.display }}）"
                    } else {
                        cell.char.toString()
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (speakEnabled) {
                    Button(onClick = onSpeak) {
                        Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text("播放")
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(MaterialTheme.colorScheme.surface),
                ) {
                    StrokeAnimation(
                        data = cell.strokes ?: return@Box,
                        paths = paths,
                        animate = animate,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                }

                Column(
                    modifier = Modifier.padding(start = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showPinyin && cell.isPolyphone) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(cell.readings.size) { index ->
                                val reading = cell.readings[index]
                                FilterChip(
                                    selected = index == cell.selectedReading,
                                    onClick = { onSelectReading(index) },
                                    label = {
                                        PinyinText(reading = reading, fontSize = 16.sp)
                                    },
                                )
                            }
                        }
                    }
                    Button(onClick = { animate = !animate }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.size(4.dp))
                        Text(if (animate) "停止动画" else "笔顺动画")
                    }
                }
            }

            if (detail.definitions.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("释义", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                detail.definitions.take(MAX_DEFINITIONS).forEach { def ->
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Text(
                            text = def.word,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(text = def.definition, style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (detail.definitions.size > MAX_DEFINITIONS) {
                    Text(
                        text = "…共 ${detail.definitions.size} 条",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val strokes = cell.strokes
            if (strokes != null && paths.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text("笔顺分解", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                StrokeBreakdown(strokes = strokes, paths = paths)
            }
        }
    }
}

/**
 * Every stroke in its own small tian zi ge, in order, with the radical marked.
 */
@Composable
private fun StrokeBreakdown(
    strokes: StrokeData,
    paths: List<android.graphics.Path>,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(paths.size) { index ->
            SingleStrokeStroke(
                strokes = strokes,
                paths = paths,
                index = index,
                modifier = Modifier.size(84.dp),
            )
        }
    }
}

@Composable
private fun SingleStrokeStroke(
    strokes: StrokeData,
    paths: List<android.graphics.Path>,
    index: Int,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier) {
        val style = com.pupil.hanzi.stroke.StrokeStyle(
            showGrid = true,
            showOutline = false,
            showRadical = true,
            showGuides = false,
            progress = -1f,
        )
        // Draw only this stroke: hide the rest by rendering a data object whose
        // radical flags match only the requested index.
        val single = strokes.copy(
            strokes = listOf(strokes.strokes[index]),
            medians = emptyList(),
            radicalStrokeIndices = listOf(0).takeIf { strokes.isRadical(index) },
        )
        drawCharacter(single, listOf(paths[index]), size.minDimension, style)
    }
}

private const val MAX_DEFINITIONS = 30
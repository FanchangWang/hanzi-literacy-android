package com.pupil.hanzi.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pupil.hanzi.data.StrokeData
import com.pupil.hanzi.ui.theme.LocalCanvasColors
import com.pupil.hanzi.stroke.StrokeGeometry
import java.util.IdentityHashMap
import kotlin.math.max

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
    // Parsing SVG paths is not free; cache per StrokeData instance. Two
    // characters whose data is equal parse to the same paths, so sharing the
    // entry is correct rather than a collision.
    val pathCache = remember { IdentityHashMap<StrokeData, List<android.graphics.Path>>() }

    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
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

/**
 * One character: a pinyin cell above a character cell, then the speaker.
 *
 * Both cells are the same width and both keep a border, so a punctuation mark
 * lines up with the hanzi around it. Punctuation repeats itself in the pinyin
 * cell in grey, as the original tool does, and takes the character cell with
 * the same box -- no grid lines, the mark simply sits in the lower left.
 */
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
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showPinyin) {
            PinyinCell(
                cell = cell,
                reading = reading,
                pickerOpen = pickerOpen,
                onOpenPicker = { pickerOpen = true },
                onPick = { idx ->
                    onSelectReading(idx)
                    pickerOpen = false
                },
                onDismissPicker = { pickerOpen = false },
            )
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

/**
 * The pinyin cell.
 *
 * A polyphone tints the WHOLE cell, matching `.pinyin.has-poly` in the
 * original stylesheet -- the background belongs to the bordered box, not to
 * the few glyphs inside it.
 */
@Composable
private fun PinyinCell(
    cell: CharCell,
    reading: com.pupil.hanzi.data.PinyinReading?,
    pickerOpen: Boolean,
    onOpenPicker: () -> Unit,
    onPick: (Int) -> Unit,
    onDismissPicker: () -> Unit,
) {
    val canvas = LocalCanvasColors.current
    val polyTint = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(
                when {
                    // The whole cell is tinted for a polyphone.
                    cell.isPolyphone -> polyTint
                    else -> canvas.cellBackground
                },
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(3.dp))
            .then(
                if (cell.isPolyphone && reading != null) {
                    Modifier.clickable(onClick = onOpenPicker)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // Punctuation repeats itself in the pinyin cell, in grey.
            cell.isPunctuation -> PunctuationPinyin(cell.char)
            reading != null -> PinyinText(
                reading = reading,
                fontSize = 15.sp,
                fontWeight = if (cell.isPolyphone) FontWeight.Bold else FontWeight.Normal,
            )
            else -> Text(
                text = cell.char.toString(),
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (pickerOpen && reading != null) {
            ReadingPicker(
                cell = cell,
                onSelect = onPick,
                onDismiss = onDismissPicker,
            )
        }
    }
}

/** The punctuation mark echoed in the pinyin cell. */
@Composable
private fun PunctuationPunctuationPlaceholder(char: Char) {
    PunctuationPinyin(char)
}

@Composable
private fun PunctuationPinyin(char: Char) {
    Text(
        text = char.toString(),
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = LocalCanvasColors.current.stroke.copy(alpha = 0.55f),
    )
}

@Composable
private fun CharacterBox(
    cell: CharCell,
    paths: List<android.graphics.Path>,
    animate: Boolean,
    onClick: () -> Unit,
) {
    val data = cell.strokes
    val ready = data != null && paths.isNotEmpty()
    val canvas = LocalCanvasColors.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(3.dp))
            .clickable(enabled = ready, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            ready -> StrokeAnimation(
                data = data,
                paths = paths,
                animate = animate,
                modifier = Modifier.fillMaxSize(),
            )

            // Punctuation takes the same box as a character: same size, same
            // border, same background, no grid lines. The mark sits in the
            // lower left, where a workbook sets it.
            cell.isPunctuation -> PunctuationCell(cell.char, Modifier.fillMaxSize())

            // A character whose strokes have not arrived yet: show the empty
            // grid rather than the glyph, so nothing is drawn and then covered.
            else -> GridPlaceholder(Modifier.fillMaxSize(), canvas.grid, canvas.cellBackground)
        }
    }
}

/**
 * A punctuation mark in a cell identical to a character's.
 *
 * The original sets the mark at 80px inside a 120px cell -- two thirds of the
 * box -- so the glyph is scaled from the cell rather than fixed, otherwise it
 * looks tiny next to a character drawn to fill its grid. It is placed toward
 * the lower left, which is where a workbook sets a mark: punctuation occupies
 * the empty corner of the square rather than the centre, and centring it makes
 * it read as another character.
 */
@Composable
private fun PunctuationCell(char: Char, modifier: Modifier = Modifier) {
    val canvas = LocalCanvasColors.current
    BoxWithConstraints(modifier = modifier) {
        val cell = maxWidth
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val side = size.minDimension
                val w = max(1f, side * 0.006f)
                drawRect(
                    color = canvas.cellBackground,
                    topLeft = androidx.compose.ui.geometry.Offset.Zero,
                    size = androidx.compose.ui.geometry.Size(side, side),
                    style = Stroke(width = w),
                )
                drawRect(
                    color = canvas.grid,
                    topLeft = androidx.compose.ui.geometry.Offset(w / 2, w / 2),
                    size = androidx.compose.ui.geometry.Size(side - w, side - w),
                    style = Stroke(width = w),
                )
            }
            Text(
                text = char.toString(),
                // 80/120 of the cell, matching the original stylesheet.
                fontSize = (cell.value * 0.66f).sp,
                color = canvas.stroke,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = (cell.value * 0.08f).dp, bottom = (cell.value * 0.06f).dp),
            )
        }
    }
}

/** The empty mi zi ge shown while a character's stroke data is loading. */
@Composable
private fun GridPlaceholder(
    modifier: Modifier = Modifier,
    gridColor: Color,
    background: Color,
) {
    Canvas(modifier) {
        drawRect(color = background)
        val side = size.minDimension
        val w = max(1f, side * 0.006f)
        drawRect(
            color = gridColor,
            topLeft = androidx.compose.ui.geometry.Offset.Zero,
            size = androidx.compose.ui.geometry.Size(side, side),
            style = Stroke(width = w),
        )
        val dash = PathEffect.dashPathEffect(floatArrayOf(side * 0.045f, side * 0.03f))
        val half = side / 2f
        drawLine(gridColor, androidx.compose.ui.geometry.Offset(half, 0f),
            androidx.compose.ui.geometry.Offset(half, side), w, pathEffect = dash)
        drawLine(gridColor, androidx.compose.ui.geometry.Offset(0f, half),
            androidx.compose.ui.geometry.Offset(side, half), w, pathEffect = dash)
        drawLine(gridColor, androidx.compose.ui.geometry.Offset.Zero,
            androidx.compose.ui.geometry.Offset(side, side), w, pathEffect = dash)
        drawLine(gridColor, androidx.compose.ui.geometry.Offset(side, 0f),
            androidx.compose.ui.geometry.Offset(0f, side), w, pathEffect = dash)
    }
}
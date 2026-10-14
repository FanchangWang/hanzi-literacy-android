package com.pupil.hanzi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.pupil.hanzi.data.StrokeData
import com.pupil.hanzi.stroke.StrokeStyle
import com.pupil.hanzi.stroke.drawCharacter

/** Milliseconds each stroke takes, plus the pause between strokes. */
private const val STROKE_MS = 420
private const val GAP_MS = 260

/**
 * Draws a character, optionally animating the stroke order on a loop.
 *
 * With animation off this is just a static render, so the same composable
 * covers both the grid cells and the detail sheet.
 */
@Composable
fun StrokeAnimation(
    data: StrokeData,
    paths: List<android.graphics.Path>,
    animate: Boolean,
    modifier: Modifier = Modifier,
    style: StrokeStyle = StrokeStyle(),
) {
    val strokeCount = data.strokeCount.coerceAtLeast(1)

    if (!animate) {
        Canvas(modifier) {
            drawCharacter(data, paths, size.minDimension, style.copy(progress = -1f))
        }
        return
    }

    // One full pass over every stroke, plus the gaps.
    val cycleMs = (STROKE_MS + GAP_MS) * strokeCount
    val progress = remember(strokeCount) { Animatable(0f) }

    LaunchedEffect(strokeCount, data) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = cycleMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )
    }

    val current = progress.value
    Canvas(modifier) {
        drawCharacter(data, paths, size.minDimension, style.copy(progress = current))
    }
}
package com.pupil.hanzi.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.pupil.hanzi.data.StrokeData
import com.pupil.hanzi.ui.theme.LocalCanvasColors
import com.pupil.hanzi.stroke.StrokeStyle
import com.pupil.hanzi.stroke.StrokeTimeline
import com.pupil.hanzi.stroke.drawCharacter

/**
 * Draws a character, optionally animating the stroke order on a loop.
 *
 * Timing comes from [StrokeTimeline]: each stroke's duration is proportional
 * to its pen length, so a long sweeping stroke and the single stroke of 一 both
 * read clearly. A fixed per-stroke time made 一 crawl through a 15-stroke
 * character.
 */
@Composable
fun StrokeAnimation(
    data: StrokeData,
    paths: List<android.graphics.Path>,
    animate: Boolean,
    modifier: Modifier = Modifier,
    style: StrokeStyle? = null,
) {
    val canvas = LocalCanvasColors.current
    val base = style ?: StrokeStyle(
        gridColor = canvas.grid,
        outlineColor = canvas.outline,
        strokeColor = canvas.stroke,
        radicalColor = canvas.radical,
        guideColor = canvas.guide,
    )

    if (!animate) {
        Canvas(modifier) {
            drawCharacter(data, paths, size.minDimension, base.copy(progress = -1f))
        }
        return
    }

    val timeline = remember(data, paths) { StrokeTimeline(data, paths) }
    val progress = remember(timeline) { Animatable(0f) }

    LaunchedEffect(timeline) {
        progress.snapTo(0f)
        progress.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = timeline.cycleMs, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        )
    }

    val current = progress.value
    Canvas(modifier) {
        drawCharacter(data, paths, size.minDimension, base.copy(progress = current))
    }
}
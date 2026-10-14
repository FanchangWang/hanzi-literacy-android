package com.pupil.hanzi.stroke

import android.graphics.Path as AndroidPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import com.pupil.hanzi.data.StrokeData
import kotlin.math.max
import kotlin.math.min

/**
 * Geometry for drawing one character inside a tian zi ge (田字格).
 *
 * Source paths live in a 1024x1024 box, so everything here is about mapping
 * that box onto whatever square the canvas happens to be. Scaling uses the
 * smaller axis and centres the result, so a non-square canvas letterboxes
 * rather than stretching the glyph.
 */
object StrokeGeometry {

    const val SOURCE_SIZE = 1024f

    /** Keeps strokes off the border, as a workbook does. */
    private const val INSET_RATIO = 0.06f

    /** Parses the SVG path strings; callers should cache the result. */
    fun parsePaths(data: StrokeData): List<AndroidPath> = data.strokes.mapNotNull { d ->
        runCatching { PathParser().parsePathString(d).toPath().asAndroidPath() }.getOrNull()
    }

    fun scaleFor(size: Float): Float = size * (1f - 2 * INSET_RATIO) / SOURCE_SIZE

    fun offsetFor(size: Float): Float = (size - SOURCE_SIZE * scaleFor(size)) / 2f

    /** Maps a point from the 1024 box into canvas pixels. */
    fun mapPoint(x: Float, y: Float, size: Float): Offset {
        val s = scaleFor(size)
        val o = offsetFor(size)
        return Offset(x * s + o, y * s + o)
    }
}

/** Palette and animation state for one character cell. */
data class StrokeStyle(
    val showGrid: Boolean = true,
    val showOutline: Boolean = true,
    val showRadical: Boolean = true,
    val showGuides: Boolean = true,
    val gridColor: Color = Color(0xFFBDBDBD),
    val outlineColor: Color = Color(0xFFE4E4E4),
    val strokeColor: Color = Color(0xFF333333),
    val radicalColor: Color = Color(0xFFE74C3C),
    val guideColor: Color = Color(0xFF4A90D9),
    /** 0..1 across the whole character; negative means "static, draw all". */
    val progress: Float = -1f,
)

/**
 * Draws one character inside a [size]-sided square.
 *
 * Layer order follows the workbook: grid, then the faint outline of strokes
 * not yet written, then the radical in red, then completed strokes. While
 * [StrokeStyle.progress] is 0..1 the stroke under the playhead is revealed
 * with PathMeasure so it grows the way a hand writes it rather than fading in.
 */
fun DrawScope.drawCharacter(
    data: StrokeData,
    paths: List<AndroidPath>,
    size: Float,
    style: StrokeStyle,
) {
    val scale = StrokeGeometry.scaleFor(size)
    val offset = StrokeGeometry.offsetFor(size)
    val strokeCount = paths.size
    if (strokeCount == 0) return

    if (style.showGrid) drawTianziGrid(size, style)

    // Work out how many strokes are finished and how far into the next one we are.
    val animating = style.progress in 0f..1f
    val workUnits = if (animating) style.progress * strokeCount else strokeCount.toFloat()
    val completed = min(strokeCount.toFloat(), workUnits).toInt()
    val partial = if (animating) (workUnits - completed).coerceIn(0f, 1f) else 0f

    val strokeWidth = max(2f, size * 0.05f)
    val solid = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val faint = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    val faintColor = style.outlineColor.copy(alpha = 0.5f)

    for (i in 0 until strokeCount) {
        val path = paths[i]
        val color = if (data.isRadical(i) && style.showRadical) {
            style.radicalColor
        } else {
            style.strokeColor
        }
        val scaled = path.scaled(scale, offset)

        when {
            i < completed -> drawPath(scaled.asComposePath(), color, style = solid)

            i == completed && partial > 0f -> {
                drawPath(revealUpTo(scaled, partial).asComposePath(), color, style = solid)
                if (style.showOutline) {
                    drawPath(scaled.asComposePath(), faintColor, style = faint)
                }
            }

            else -> if (style.showOutline && !animating) {
                drawPath(scaled.asComposePath(), faintColor, style = faint)
            }
        }
    }

    // Pen-path guides, only while the animation is running.
    if (animating && style.showGuides && style.showOutline) {
        val visible = min(data.medians.size, completed + if (partial > 0f) 1 else 0)
        if (visible > 0) {
            val guide = Stroke(
                width = max(1f, size * 0.012f),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(size * 0.035f, size * 0.03f)),
            )
            for (i in 0 until visible) {
                val pts = data.medians.getOrNull(i) ?: continue
                val p = AndroidPath()
                pts.forEachIndexed { index, xy ->
                    if (xy.size < 2) return@forEachIndexed
                    val at = StrokeGeometry.mapPoint(xy[0].toFloat(), xy[1].toFloat(), size)
                    if (index == 0) p.moveTo(at.x, at.y) else p.lineTo(at.x, at.y)
                }
                drawPath(p.asComposePath(), style.guideColor, style = guide)
            }
        }
    }
}

/** 田字格: solid border, dashed centre cross. */
private fun DrawScope.drawTianziGrid(size: Float, style: StrokeStyle) {
    val w = max(1f, size * 0.006f)
    drawRect(
        color = style.gridColor,
        topLeft = Offset.Zero,
        size = Size(size, size),
        style = Stroke(width = w),
    )
    val dash = PathEffect.dashPathEffect(floatArrayOf(size * 0.045f, size * 0.03f))
    val half = size / 2f
    drawLine(style.gridColor, Offset(half, 0f), Offset(half, size), w, pathEffect = dash)
    drawLine(style.gridColor, Offset(0f, half), Offset(size, half), w, pathEffect = dash)
}

/** Applies scale + translate to a path expressed in the 1024 source box. */
private fun AndroidPath.scaled(scale: Float, offset: Float): AndroidPath {
    val out = AndroidPath()
    val matrix = android.graphics.Matrix().apply {
        postScale(scale, scale)
        postTranslate(offset, offset)
    }
    transform(matrix, out)
    return out
}

/**
 * The leading [fraction] of [path], so a stroke looks written rather than
 * cross-faded in.
 *
 * Glyph contours can be multi-contour while PathMeasure.getSegment walks only
 * one, so every contour contributes the same fraction.
 */
private fun revealUpTo(path: AndroidPath, fraction: Float): AndroidPath {
    if (fraction >= 1f) return path
    if (fraction <= 0f) return AndroidPath()

    val out = AndroidPath()
    val measure = android.graphics.PathMeasure(path, false)
    if (measure.length <= 0f) return out

    do {
        val len = measure.length
        if (len > 0f) measure.getSegment(0f, len * fraction, out, true)
    } while (measure.nextContour())
    return out
}
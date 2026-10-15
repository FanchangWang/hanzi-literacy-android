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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import com.pupil.hanzi.data.StrokeData
import kotlin.math.max
import kotlin.math.min

/**
 * Geometry for drawing one character inside a mi zi ge (米字格).
 *
 * The source box is 1024x1024 and **y grows upwards**, the opposite of the
 * screen, so every mapping here inverts y:
 *  - 上 (shang): stroke 2 is the bottom bar, y 30..178. Small y is the BOTTOM.
 *  - 下 (xia):  stroke 0 is the top bar, y 625..801. Large y is the TOP.
 *
 * The glyph box is also not 0..1024: hanzi-writer declares the bounds as
 * {0, -124} to {1024, 900}, and measuring the data gives y -119..898, which
 * matches. Centring on a 1024 square instead leaves the character sitting low.
 */
object StrokeGeometry {

    /** Real glyph extent in the source box, matching hanzi-writer. */
    const val MIN_X = 0f
    const val MAX_X = 1024f
    const val MIN_Y = -124f
    const val MAX_Y = 900f

    private const val BOX_W = MAX_X - MIN_X
    private const val BOX_H = MAX_Y - MIN_Y

    /** Keeps strokes off the border, as a workbook does. */
    private const val INSET_RATIO = 0.06f

    fun parsePaths(data: StrokeData): List<AndroidPath> = data.strokes.mapNotNull { d ->
        runCatching { PathParser().parsePathString(d).toPath().asAndroidPath() }.getOrNull()
    }

    fun scaleFor(size: Float): Float = min(
        size * (1f - 2 * INSET_RATIO) / BOX_W,
        size * (1f - 2 * INSET_RATIO) / BOX_H,
    )

    /** Canvas-space position of the box origin, after centring. */
    fun originFor(size: Float): Pair<Float, Float> {
        val s = scaleFor(size)
        val usable = size * (1f - 2 * INSET_RATIO)
        val x = size * INSET_RATIO + (usable - BOX_W * s) / 2f
        val y = size * INSET_RATIO + (usable - BOX_H * s) / 2f
        return x - MIN_X * s to y + MAX_Y * s
    }

    /** Maps a point from the y-up source box into y-down screen pixels. */
    fun mapPoint(x: Float, y: Float, size: Float): Offset {
        val s = scaleFor(size)
        val (ox, oy) = originFor(size)
        return Offset(x * s + ox, oy - y * s)
    }

    /** Fallback reveal for strokes with no usable median. */
    fun revealUpTo(path: AndroidPath, fraction: Float): AndroidPath {
        if (fraction >= 1f) return path
        if (fraction <= 0f) return AndroidPath()
        val measure = android.graphics.PathMeasure(path, false)
        val total = measure.length
        if (total <= 0f) return AndroidPath()
        val out = AndroidPath()
        measure.getSegment(0f, total * fraction, out, true)
        return out
    }
}

/** Palette and animation state for one character cell. */
data class StrokeStyle(
    val showGrid: Boolean = true,
    val showOutline: Boolean = true,
    val showRadical: Boolean = true,
    val showGuides: Boolean = false,
    val gridColor: Color = Color(0xFFBDBDBD),
    val outlineColor: Color = Color(0xFFEDEDED),
    val strokeColor: Color = Color(0xFF2B2B2B),
    val radicalColor: Color = Color(0xFFE74C3C),
    val guideColor: Color = Color(0xFF4A90D9),
    /** 0..1 across the whole character; negative means "static, draw all". */
    val progress: Float = -1f,
) {
    /**
     * Pen width that covers a stroke's own outline: hanzi-writer uses 200 on the
     * 1024 box. The animated pen and the finished outline must agree or the
     * character changes weight when the loop ends.
     */
    fun penWidth(side: Float): Float = side * 0.196f
}

/**
 * Draws one character inside a [size]-sided square.
 *
 * [strokesToDraw] fixes how many strokes are inked; every stroke is still drawn
 * as a faint silhouette. Passing null follows [StrokeStyle.progress] instead,
 * which is the animated path.
 */
fun DrawScope.drawCharacter(
    data: StrokeData,
    paths: List<AndroidPath>,
    size: Float,
    style: StrokeStyle,
    strokesToDraw: Int? = null,
) {
    if (paths.isEmpty() || size <= 0f) return
    val side = size

    if (style.showGrid) drawGrid(side, style)

    val scale = StrokeGeometry.scaleFor(side)
    val (ox, oy) = StrokeGeometry.originFor(side)
    val strokeCount = paths.size

    // One matrix for every layer, or the silhouette and the ink drift apart.
    val scaled = paths.map { it.transformed(scale, ox, oy) }
    val animating = strokesToDraw == null && style.progress in 0f..1f

    val completed: Int
    val partial: Float
    var timeline: StrokeTimeline? = null
    if (strokesToDraw != null) {
        completed = strokesToDraw.coerceIn(0, strokeCount)
        partial = 0f
    } else if (animating) {
        timeline = StrokeTimeline(data, scaled)
        val loc = timeline.locate(style.progress)
        // A null location means the timeline has run past the last stroke,
        // which happens at the end of every loop. Defaulting to 0 there made
        // the whole character vanish for most of each cycle.
        completed = loc?.first ?: strokeCount
        partial = loc?.second ?: 0f
    } else {
        completed = strokeCount
        partial = 0f
    }

    // Ghost: the whole character, so the target shape stays visible.
    //
    // Drawn at full strength. cnchar paints its silhouette as a flat #DDD in a
    // separate pass (hanzi-writer.js:923); multiplying an alpha on top of an
    // already-light grey washed the shadow out to near-invisibility.
    if (style.showOutline) {
        for (p in scaled) drawPath(p.asComposePath(), style.outlineColor)
    }

    val penWidth = style.penWidth(side)

    for (i in 0 until strokeCount) {
        val color = if (data.isRadical(i) && style.showRadical) {
            style.radicalColor
        } else {
            style.strokeColor
        }
        when {
            i < completed -> drawPath(scaled[i].asComposePath(), color)

            i == completed && partial > 0f -> {
                val pen = timeline?.medianSegment(i, partial)
                if (pen != null) {
                    clipAndDrawPen(pen, color, side, penWidth, scaled[i])
                } else {
                    drawPath(
                        StrokeGeometry.revealUpTo(scaled[i], partial).asComposePath(),
                        color,
                    )
                }
            }
        }
    }

    // Pen-path guides, only while the animation is running.
    if (animating && style.showGuides) {
        val visible = min(data.medians.size, completed + if (partial > 0f) 1 else 0)
        val guide = Stroke(
            width = max(1f, side * 0.012f),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(side * 0.035f, side * 0.03f)),
        )
        for (i in 0 until visible) {
            val pts = data.medians.getOrNull(i) ?: continue
            val p = AndroidPath()
            pts.forEachIndexed { index, xy ->
                if (xy.size < 2) return@forEachIndexed
                val at = StrokeGeometry.mapPoint(xy[0], xy[1], side)
                if (index == 0) p.moveTo(at.x, at.y) else p.lineTo(at.x, at.y)
            }
            drawPath(p.asComposePath(), style.guideColor, style = guide)
        }
    }
}

/**
 * Fills the written part of a stroke: a pen-width line along the median,
 * clipped to that stroke's own outline so the reveal cannot spill sideways.
 *
 * The clip has to go through the native canvas. Re-drawing the outline
 * afterwards instead paints the whole stroke at once, which is what made a
 * character look as though only its radical had been written.
 */
private fun DrawScope.clipAndDrawPen(
    pen: List<Pair<Float, Float>>,
    color: Color,
    side: Float,
    penWidth: Float,
    outline: AndroidPath,
) {
    if (pen.size < 2) return
    val s = StrokeGeometry.scaleFor(side)
    val (ox, oy) = StrokeGeometry.originFor(side)

    val penPath = AndroidPath()
    pen.forEachIndexed { i, (x, y) ->
        val px = x * s + ox
        val py = oy - y * s
        if (i == 0) penPath.moveTo(px, py) else penPath.lineTo(px, py)
    }

    val canvas = drawContext.canvas.nativeCanvas
    val checkpoint = canvas.save()
    canvas.clipPath(outline)
    drawPath(
        penPath.asComposePath(),
        color,
        style = Stroke(width = penWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
    canvas.restoreToCount(checkpoint)
}

/**
 * 米字格: solid border, dashed centre cross, dashed diagonals.
 */
private fun DrawScope.drawGrid(size: Float, style: StrokeStyle) {
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
    drawLine(style.gridColor, Offset(0f, 0f), Offset(size, size), w, pathEffect = dash)
    drawLine(style.gridColor, Offset(size, 0f), Offset(0f, size), w, pathEffect = dash)
}

/** Applies scale, y-flip and translate to a path in the y-up source box. */
private fun AndroidPath.transformed(scale: Float, ox: Float, oy: Float): AndroidPath {
    val out = AndroidPath()
    transform(
        android.graphics.Matrix().apply {
            postScale(scale, -scale)
            postTranslate(ox, oy)
        },
        out,
    )
    return out
}
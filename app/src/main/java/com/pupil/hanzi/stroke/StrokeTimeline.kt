package com.pupil.hanzi.stroke

import android.graphics.Path as AndroidPath
import com.pupil.hanzi.data.StrokeData
import kotlin.math.sqrt

/**
 * Builds the animated form of one character, following hanzi-writer -- the
 * library cnchar-draw is built on -- rather than inventing an equivalent.
 *
 * The reveal follows the stroke's *median* (the pen path), not the outline: an
 * outline is a closed contour whose start point is arbitrary, so trimming it
 * animates a 横 backwards and leaves a 竖 filling from the wrong end.
 *
 * hanzi-writer applies exactly one transform to the median before drawing it
 * as the pen: `extendStart(points, 100)`, which nudges the first point along
 * the direction of the first segment so the round cap starts at the true
 * origin. There is no compensating step -- an earlier version of this file
 * added a "subtract" that shifted the whole path, which double-counted the cap
 * and left short diagonal strokes such as the 丿 in 女 running backwards.
 */
class StrokeTimeline(
    val data: StrokeData,
    val paths: List<AndroidPath>,
) {
    /**
     * Pen path per stroke in the 1024 source box. Empty when the data carries
     * no usable median, in which case the caller trims the outline instead.
     */
    private val medians: List<List<Pair<Float, Float>>> = data.medians.map { stroke ->
        val pts = stroke.mapNotNull { xy ->
            if (xy.size < 2) null else xy[0] to xy[1]
        }
        if (pts.size < 2) emptyList() else extendStart(pts, LEAD_IN)
    }

    val strokeCount: Int get() = paths.size

    /** Per-stroke duration in ms, proportional to pen length. */
    val durations: List<Float> by lazy {
        val lengths = medians.map { polylineLength(it) }
            .map { if (it > 0f) it else 1000f }
        StrokeTiming.durations(lengths)
    }

    /** Total loop time including the scaled gaps. */
    val cycleMs: Int by lazy { StrokeTiming.cycleMs(durations) }

    /**
     * Which stroke is being written, and how far into it, for a global 0..1
     * position in the loop.
     */
    fun locate(progress: Float): Pair<Int, Float>? = StrokeTiming.locate(progress, durations)

    /**
     * The leading [fraction] of stroke [index] in source-box coordinates, or
     * null when the stroke has no usable median.
     */
    fun medianSegment(index: Int, fraction: Float): List<Pair<Float, Float>>? {
        val pts = medians.getOrNull(index).orEmpty()
        if (pts.size < 2) return null
        return truncate(pts, fraction.coerceIn(0f, 1f))
    }

    companion object {
        /**
         * The lead-in hanzi-writer passes to `extendStart`: 100 on the 1024
         * box, half its 200-unit pen width.
         */
        const val LEAD_IN = 100f

        /**
         * hanzi-writer's `extendStart`: move the first point [amount] along the
         * direction of the first segment, keeping its position in the list.
         *
         * The original origin is *replaced*, not kept, so the pen's round cap
         * sits on the stroke's true beginning. The step is not clamped to the
         * length of the first segment: a short one, such as the start of the
         * 丿 in 女, would otherwise leave the pen barely moved and the stroke
         * would appear to run the wrong way.
         */
        fun extendStart(
            points: List<Pair<Float, Float>>,
            amount: Float,
        ): List<Pair<Float, Float>> {
            if (points.size < 2 || amount <= 0f) return points
            val (x0, y0) = points[0]
            val (x1, y1) = points[1]
            val dx = x1 - x0
            val dy = y1 - y0
            val len = sqrt(dx * dx + dy * dy)
            if (len <= 0f) return points
            val k = amount / len
            return points.mapIndexed { i, (x, y) ->
                if (i == 0) (x + dx * k) to (y + dy * k) else x to y
            }
        }

        /**
         * Walks [points] up to [fraction] of their total length, always keeping
         * at least two so a sliver still draws.
         */
        fun truncate(
            points: List<Pair<Float, Float>>,
            fraction: Float,
        ): List<Pair<Float, Float>> {
            if (points.size < 2) return points
            if (fraction >= 1f) return points
            val total = polylineLength(points)
            if (total <= 0f) return points

            val target = total * fraction
            val out = ArrayList<Pair<Float, Float>>(points.size)
            out += points.first()
            var travelled = 0f
            for (i in 0 until points.size - 1) {
                if (travelled >= target) break
                val (x0, y0) = points[i]
                val (x1, y1) = points[i + 1]
                val segLen = sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
                if (segLen <= 0f) continue
                val remaining = target - travelled
                if (remaining >= segLen) {
                    out += (x1 to y1)
                    travelled += segLen
                } else {
                    val t = remaining / segLen
                    out += ((x0 + (x1 - x0) * t) to (y0 + (y1 - y0) * t))
                    break
                }
            }
            return if (out.size < 2) points else out
        }

        fun polylineLength(points: List<Pair<Float, Float>>): Float {
            var total = 0f
            for (i in 0 until points.size - 1) {
                val (x0, y0) = points[i]
                val (x1, y1) = points[i + 1]
                total += sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0))
            }
            return total
        }
    }
}

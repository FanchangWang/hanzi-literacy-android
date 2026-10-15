package com.pupil.hanzi.stroke

/**
 * How long each stroke of a character should take.
 *
 * Time follows pen length, with a floor so a very short stroke is still
 * visible. A flat per-stroke floor is wrong in the other direction too: in 懂
 * the first two strokes are dots worth a few tens of units, and stretching them
 * to the same duration as a full sweep turns writing into a sequence of pauses.
 */
object StrokeTiming {

    /** Base speed, tuned so a typical stroke reads clearly at about a second. */
    const val MS_PER_UNIT = 1.05f

    /** Absolute floor so a degenerate stroke is still a frame. */
    const val MIN_STROKE_MS = 120f

    /** Pause between strokes, scaled down for busy characters. */
    const val GAP_MS = 220f

    /** Share of the budget reserved for the pauses. */
    const val GAP_SHARE = 0.18f

    /** Ceiling for the whole loop. */
    const val MAX_TOTAL_MS = 9000f

    /**
     * The pause *between two strokes* for a character of [strokeCount] strokes.
     *
     * This is the per-gap value. [scaledGaps] is the total across the whole
     * character, and confusing the two is what made [locate] run the clock
     * backwards: subtracting the total once per stroke meant a 15-stroke
     * character never got past its fourth.
     */
    fun scaledGap(strokeCount: Int): Float {
        if (strokeCount <= 1) return 0f
        val cap = (MAX_TOTAL_MS * GAP_SHARE).coerceAtLeast(GAP_MS)
        return minOf(GAP_MS, cap / (strokeCount - 1))
    }

    /** Total pause time used for a character of [strokeCount] strokes. */
    fun scaledGaps(strokeCount: Int): Float =
        scaledGap(strokeCount) * (strokeCount - 1).coerceAtLeast(0)

    /**
     * Per-stroke durations in ms, in the same order as [lengths].
     *
     * Gaps are budgeted alongside the strokes: at a fixed 220ms, forty of them
     * would consume most of the loop on silence.
     */
    fun durations(lengths: List<Float>): List<Float> {
        if (lengths.isEmpty()) return emptyList()

        val count = lengths.size
        val penTotal = lengths.sum().coerceAtLeast(1f)
        val gaps = scaledGaps(count)
        val drawBudget = (MAX_TOTAL_MS - gaps).coerceAtLeast(1f)

        val scale = minOf(MS_PER_UNIT, drawBudget / penTotal)
        var out = lengths.map { maxOf(MIN_STROKE_MS, it * scale) }

        // The floor can push the total past the budget; shrink uniformly so the
        // character keeps its rhythm instead of one stroke eating the loop.
        val total = out.sum()
        if (total > drawBudget) {
            val k = drawBudget / total
            out = out.map { maxOf(60f, it * k) }
        }
        return out
    }

    /** Total loop time in ms, including the scaled gaps. */
    fun cycleMs(durations: List<Float>): Int {
        if (durations.isEmpty()) return 1000
        return (durations.sum() + scaledGaps(durations.size)).toInt()
    }

    /**
     * Maps a global 0..1 position in the loop onto a stroke and a fraction of
     * that stroke.
     *
     * @return (strokeIndex, 0..1 within it), or null when the character has
     *         not started or has already finished.
     */
    fun locate(progress: Float, durations: List<Float>): Pair<Int, Float>? {
        if (durations.isEmpty() || progress <= 0f || progress >= 1f) return null
        val gap = scaledGap(durations.size)
        val total = durations.sum() + gap * (durations.size - 1).coerceAtLeast(0)
        if (total <= 0f) return null

        var t = progress * total
        for (i in durations.indices) {
            val d = durations[i]
            if (t < d) return i to (t / d).coerceIn(0f, 1f)
            t -= d
            if (i < durations.size - 1) {
                // While in a gap the previous stroke stays complete.
                if (t < gap) return i to 1f
                t -= gap
            }
        }
        return null
    }
}
package com.pupil.hanzi.stroke

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Two facts about the stroke data that are easy to get wrong and invisible in
 * the code, so they are pinned here.
 *
 * 1. **y grows upwards.** For 上 the bottom bar spans y 30..178, so small y is
 *    the bottom; for 下 the top bar spans y 625..801, so large y is the top.
 *
 * 2. **The glyph box is not 0..1024.** hanzi-writer, which cnchar-draw is
 *    built on, declares the bounds as `{0, -124}` to `{1024, 900}`; measuring
 *    every stroke in the data gives x 5..1023 and y -119..898, which matches.
 *    Centring on a 1024 square instead leaves the character sitting low.
 */
class StrokeGeometryTest {

    @Test
    fun `source y is up so the mapping inverts it`() {
        val side = 200f
        val top = StrokeGeometry.mapPoint(0f, StrokeGeometry.MAX_Y, side)
        val bottom = StrokeGeometry.mapPoint(0f, StrokeGeometry.MIN_Y, side)
        assertTrue(
            "MAX_Y (top) must land above MIN_Y (bottom): top=${top.y} bottom=${bottom.y}",
            top.y < bottom.y,
        )
    }

    @Test
    fun `the glyph is centred vertically in the canvas`() {
        val side = 400f
        val top = StrokeGeometry.mapPoint(512f, StrokeGeometry.MAX_Y, side)
        val bottom = StrokeGeometry.mapPoint(512f, StrokeGeometry.MIN_Y, side)
        val topGap = top.y
        val bottomGap = side - bottom.y
        assertEquals(
            "glyph must sit centred, not low: topGap=$topGap bottomGap=$bottomGap",
            topGap, bottomGap, 1f,
        )
    }

    @Test
    fun `the glyph is centred horizontally in the canvas`() {
        val side = 400f
        val left = StrokeGeometry.mapPoint(StrokeGeometry.MIN_X, 0f, side)
        val right = StrokeGeometry.mapPoint(StrokeGeometry.MAX_X, 0f, side)
        assertEquals("left gap ${left.x}", side - right.x, left.x, 1f)
    }

    @Test
    fun `the glyph fills the padded canvas`() {
        val side = 400f
        val top = StrokeGeometry.mapPoint(0f, StrokeGeometry.MAX_Y, side)
        val bottom = StrokeGeometry.mapPoint(0f, StrokeGeometry.MIN_Y, side)
        val height = bottom.y - top.y
        assertTrue("glyph too small: $height", height >= side * 0.85f)
        assertTrue("glyph too large: $height", height <= side * 0.92f)
    }
}

class StrokeTimelineTest {

    @Test
    fun `truncate grows along the pen path`() {
        val pts = listOf(0f to 0f, 100f to 0f, 100f to 100f)
        val half = StrokeTimeline.truncate(pts, 0.5f)
        assertEquals(2, half.size)
        assertEquals(0f, half[0].first, 0.01f)
        assertEquals(100f, half[1].first, 0.01f)
    }

    @Test
    fun `truncate keeps at least two points so a sliver stays drawable`() {
        val pts = listOf(0f to 0f, 100f to 0f)
        assertTrue(StrokeTimeline.truncate(pts, 0.001f).size >= 2)
    }

    @Test
    fun `extendStart advances the origin along the writing direction`() {
        // hanzi-writer's extendStart(p0, p1, amount) is built on p1 - p0, so
        // the pen starts further along the stroke rather than behind it. That
        // is what lets the round cap cover the stroke from its true beginning.
        val pts = listOf(100f to 500f, 300f to 500f, 300f to 700f)
        val out = StrokeTimeline.extendStart(pts, 50f)

        assertEquals("list length is unchanged", pts.size, out.size)
        assertTrue(
            "must advance along the stroke: ${out[0]} vs ${pts[0]}",
            out[0].first > pts[0].first,
        )
        assertEquals("y unchanged for a horizontal stroke", pts[0].second, out[0].second, 0.01f)
        for (i in 1 until pts.size) {
            assertEquals("point $i", pts[i], out[i])
        }
    }

    @Test
    fun `extendStart is not clamped to a short first segment`() {
        // The 丿 in 女 begins with a very short segment. Clamping the lead-in to
        // that length left the pen stuck at the origin, which is what made
        // short diagonals look reversed.
        val pts = listOf(500f to 800f, 505f to 795f, 300f to 400f)
        val out = StrokeTimeline.extendStart(pts, StrokeTimeline.LEAD_IN)

        val dx = out[0].first - pts[0].first
        val dy = out[0].second - pts[0].second
        assertEquals(
            "lead-in must be the full width even on a short segment",
            StrokeTimeline.LEAD_IN, hypot(dx, dy), 0.5f,
        )
    }
}

class StrokeTimingTest {

    @Test
    fun `durations are proportional to length not fixed`() {
        val dShort = StrokeTiming.durations(listOf(400f)).single()
        val dLong = StrokeTiming.durations(listOf(2000f)).single()
        assertTrue("long stroke should take longer: $dLong vs $dShort", dLong > dShort)
    }

    @Test
    fun `a short character honours the full per stroke floor`() {
        val d = StrokeTiming.durations(listOf(1f, 1f, 1f))
        d.forEach { assertTrue("too fast: $it", it >= StrokeTiming.MIN_STROKE_MS) }
    }

    @Test
    fun `the busiest character still fits the budget`() {
        val busy = StrokeTiming.durations(List(33) { 2000f })
        assertTrue(
            "33-stroke cycle too long: ${StrokeTiming.cycleMs(busy)}ms",
            StrokeTiming.cycleMs(busy) <= StrokeTiming.MAX_TOTAL_MS.toInt(),
        )
    }

    @Test
    fun `locate walks the timeline in order`() {
        val durations = listOf(1000f, 1000f)
        assertEquals(0, StrokeTiming.locate(0.01f, durations)?.first)
        assertEquals(1, StrokeTiming.locate(0.9f, durations)?.first)
    }
}
/**
 * Regression tests for the timeline walk.
 *
 * `locate` once subtracted the *total* gap budget on every stroke instead of
 * the per-gap value, which ran the clock backwards so fast that a 15-stroke
 * character never got past its fourth stroke: the UI showed a few strokes and
 * then stopped. These tests walk the whole loop and assert that every stroke
 * is reachable.
 */
class StrokeTimelineCoverageTest {

    /** The real pen lengths of 懂, whose first two strokes are short dots. */
    private val dongLengths = listOf(
        183f, 104f, 827f, 469f, 190f, 205f, 261f, 562f,
        206f, 527f, 175f, 209f, 532f, 270f, 543f,
    )

    @Test
    fun `every stroke is reachable across the whole loop`() {
        val durations = StrokeTiming.durations(dongLengths)
        val reached = sortedSetOf<Int>()
        val samples = 4000
        for (i in 0..samples) {
            StrokeTiming.locate(i.toFloat() / samples, durations)?.let { reached += it.first }
        }
        assertEquals(
            "not every stroke is reachable: $reached",
            durations.indices.toSet(), reached.toSet(),
        )
    }

    @Test
    fun `the stroke index never goes backwards`() {
        val durations = StrokeTiming.durations(dongLengths)
        var previous = -1
        var regressions = 0
        val samples = 4000
        for (i in 0..samples) {
            val r = StrokeTiming.locate(i.toFloat() / samples, durations) ?: continue
            if (r.first < previous) regressions++
            previous = r.first
        }
        assertEquals("the timeline must not rewind", 0, regressions)
    }

    @Test
    fun `a stroke stays complete through its gap and the next one starts after`() {
        val durations = StrokeTiming.durations(dongLengths)
        val gap = StrokeTiming.scaledGap(durations.size)
        val cycle = StrokeTiming.cycleMs(durations).toFloat()

        // Sample densely inside the gap after stroke 0.
        val d0 = durations[0]
        val inside = (d0 + gap * 0.5f) / cycle
        val r = StrokeTiming.locate(inside, durations)
        assertEquals("stroke 0 must read as complete during its gap", 0, r?.first)
        assertEquals(1f, r?.second ?: 0f, 0.001f)

        // And just past it, stroke 1 has begun.
        val justAfter = (d0 + gap * 1.5f) / cycle
        val r2 = StrokeTiming.locate(justAfter, durations)
        assertEquals("stroke 1 must have started", 1, r2?.first)
    }

    @Test
    fun `gaps are counted per stroke not once for the character`() {
        assertEquals(
            "total gaps must be per-gap x (count - 1)",
            StrokeTiming.scaledGap(15) * 14f,
            StrokeTiming.scaledGaps(15),
            0.01f,
        )
    }
}

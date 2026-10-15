package com.pupil.hanzi.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Stroke data for one character, from assets/draw_all/shard-*.json.
 *
 * The SVG paths and medians live in a 1024x1024 box (Make Me a Hanzi /
 * hanzi-writer-data format), scaled at draw time.
 */
@Serializable
data class StrokeData(
    /** One SVG path per stroke, in writing order. */
    val strokes: List<String> = emptyList(),
    /**
     * One polyline per stroke: the pen path, used for guides and tracing.
     *
     * Coordinates are Float, not Int. 32 of the 9574 characters carry
     * fractional values -- 懂 has a median point of [755, 598.25] -- and
     * declaring Int makes the decoder throw on those characters. The largest
     * fraction seen is 0.89 of a unit out of 1024, so truncating to Int would
     * buy nothing and cost a crash.
     */
    val medians: List<List<List<Float>>> = emptyList(),
    /**
     * Indices into [strokes] that form the radical. Absent for 556 of the
     * 9574 characters, which is why this is nullable rather than empty.
     */
    @SerialName("radStrokes")
    val radicalStrokeIndices: List<Int>? = null,
) {
    val strokeCount: Int get() = strokes.size

    /** True when [index] belongs to the radical, i.e. should render red. */
    fun isRadical(index: Int): Boolean =
        radicalStrokeIndices?.contains(index) == true
}
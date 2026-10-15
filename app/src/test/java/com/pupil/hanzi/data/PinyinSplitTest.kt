package com.pupil.hanzi.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The initial/final split decides where the red ends and the blue begins.
 * These are the exact syllables from the device screenshot.
 */
class PinyinSplitTest {

    private fun split(key: String): Pair<String, String> {
        val r = PinyinService.readingsOf('一')
            .firstOrNull { it.key == key }
        return if (r != null) r.initial to r.final else "" to ""
    }

    @Test
    fun `initial length equals the red portion of the display`() {
        // 懂 -> dong3 -> "dǒng": 'd' red, 'ǒng' blue.
        val d = PinyinService.readingsOf('懂').first()
        assertEquals("dǒng", d.display)
        assertEquals("d", d.initial)
        assertEquals("ǒng", d.final)
        assertEquals(d.display, d.initial + d.final)
        // The red span is the first initial.length characters of the display.
        assertEquals("d", d.display.substring(0, d.initial.length))
    }

    @Test
    fun `retroflex initial is two characters`() {
        val z = PinyinService.readingsOf('重').first { it.key == "zhong4" }
        assertEquals("zhòng", z.display)
        assertEquals("zh", z.initial)
        assertEquals("òng", z.final)
    }

    @Test
    fun `the first reading of zhong is the fourth tone`() {
        val first = PinyinService.readingsOf('重').first()
        assertEquals("zhong4", first.key)
        assertEquals("zhòng", first.display)
    }

    @Test
    fun `every reading splits to exactly its display`() {
        for (c in "懂好重长行人一不和水火天地你我他门山出月日风雨电都还少") {
            for (r in PinyinService.readingsOf(c)) {
                assertEquals(
                    "split mismatch for $c / ${r.key}",
                    r.display, r.initial + r.final,
                )
                assertTrue2("initial longer than display for $c", r.initial.length <= r.display.length)
            }
        }
    }

    private fun assertTrue2(msg: String, cond: Boolean) = org.junit.Assert.assertTrue(msg, cond)
}
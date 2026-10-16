package com.pupil.hanzi.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The history is ordered newest first and is a set of the texts the user
 * studies: the same text twice must not appear twice, and a long-lived install
 * must not grow the list without bound.
 */
class HistoryTest {

    @Test
    fun `a new text goes to the front`() {
        assertEquals(listOf("乙", "甲"), addToHistory(listOf("甲"), "乙"))
    }

    @Test
    fun `an existing text moves to the front instead of repeating`() {
        assertEquals(listOf("乙", "甲", "丙"), addToHistory(listOf("甲", "乙", "丙"), "乙"))
        // Already first: the rest keep their order.
        assertEquals(listOf("甲", "乙", "丙"), addToHistory(listOf("甲", "乙", "丙"), "甲"))
    }

    @Test
    fun `the list is capped, oldest dropped`() {
        val history = listOf("甲", "乙", "丙")
        assertEquals(listOf("丁", "甲", "乙"), addToHistory(history, "丁", limit = 3))
    }

    @Test
    fun `an empty text changes nothing`() {
        assertEquals(listOf("甲"), addToHistory(listOf("甲"), ""))
        assertEquals(emptyList<String>(), addToHistory(emptyList(), ""))
    }

    @Test
    fun `a text holding newlines survives the round trip`() {
        val history = listOf("你好\n世界", "「引号」，标点。")
        assertEquals(history, decodeHistory(encodeHistory(history)))
    }

    @Test
    fun `an unreadable store reads as empty rather than throwing`() {
        assertEquals(emptyList<String>(), decodeHistory(null))
        assertEquals(emptyList<String>(), decodeHistory("not json"))
    }
}

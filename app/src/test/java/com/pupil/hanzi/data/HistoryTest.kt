package com.pupil.hanzi.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The history is ordered newest first and is a set of the texts the user
 * studies: the same text twice must not appear twice, and a long-lived install
 * must not grow the list without bound.
 */
class HistoryTest {

    /** Entries with no time, for the cases that do not care when. */
    private fun entries(vararg texts: String): List<HistoryEntry> =
        texts.map { HistoryEntry(it) }

    @Test
    fun `a new text goes to the front`() {
        assertEquals(
            listOf(HistoryEntry("乙", 9), HistoryEntry("甲", 0)),
            addToHistory(entries("甲"), "乙", timeMillis = 9),
        )
    }

    @Test
    fun `an existing text moves to the front and takes the newer time`() {
        val history = listOf(HistoryEntry("甲", 1), HistoryEntry("乙", 2), HistoryEntry("丙", 3))
        assertEquals(
            listOf(HistoryEntry("乙", 9), HistoryEntry("甲", 1), HistoryEntry("丙", 3)),
            addToHistory(history, "乙", timeMillis = 9),
        )
        // Already first: the others keep their order, the time is refreshed.
        assertEquals(
            listOf(HistoryEntry("甲", 9), HistoryEntry("乙", 2), HistoryEntry("丙", 3)),
            addToHistory(history, "甲", timeMillis = 9),
        )
    }

    @Test
    fun `past the cap the oldest entry goes`() {
        assertEquals(
            listOf(HistoryEntry("丁", 9), HistoryEntry("甲", 0), HistoryEntry("乙", 0)),
            addToHistory(entries("甲", "乙", "丙"), "丁", timeMillis = 9, limit = 3),
        )
    }

    @Test
    fun `an empty text changes nothing`() {
        assertEquals(entries("甲"), addToHistory(entries("甲"), "", timeMillis = 9))
        assertEquals(emptyList<HistoryEntry>(), addToHistory(emptyList(), "", timeMillis = 9))
    }

    @Test
    fun `a text holding newlines survives the round trip`() {
        val history = listOf(
            HistoryEntry("你好\n世界", 1_700_000_000_000),
            HistoryEntry("「引号」，标点。", 1_700_000_001_000),
        )
        assertEquals(history, decodeHistory(encodeHistory(history)))
    }

    @Test
    fun `a store of plain texts, from before entries had a time, still reads`() {
        assertEquals(
            listOf(HistoryEntry("甲", 0), HistoryEntry("乙", 0)),
            decodeHistory("""["甲","乙"]"""),
        )
    }

    @Test
    fun `an unreadable store reads as empty rather than throwing`() {
        assertEquals(emptyList<HistoryEntry>(), decodeHistory(null))
        assertEquals(emptyList<HistoryEntry>(), decodeHistory("not json"))
    }
}

package com.pupil.hanzi.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The grid breaks a row wherever the input had a newline. Getting this wrong
 * shows up as characters silently moved to the wrong line, so the rules -- one
 * break per run of newlines, none at the ends -- are pinned here.
 */
class GridRowsTest {

    /** One cell per character, the way the view model builds them. */
    private fun cellsOf(text: String): List<CharCell> = text.map { char ->
        CharCell(
            char = char,
            readings = emptyList(),
            isPunctuation = !com.pupil.hanzi.data.PinyinService.isHan(char),
        )
    }

    private fun rowsOf(text: String, columns: Int): List<List<Int>> =
        gridRows(cellsOf(text), columns)

    /** Row contents as text, so the expectations read like the input. */
    private fun linesOf(text: String, columns: Int): List<String> =
        rowsOf(text, columns).map { row ->
            row.joinToString("") { cellsOf(text)[it].char.toString() }
        }

    @Test
    fun `a line longer than the column count wraps`() {
        assertEquals(listOf(listOf(0, 1, 2), listOf(3, 4)), rowsOf("你好世界吗", 3))
    }

    @Test
    fun `a newline ends the row`() {
        assertEquals(listOf("你好", "世界"), linesOf("你好\n世界", 6))
    }

    @Test
    fun `a run of newlines is one break`() {
        assertEquals(listOf("你", "好"), linesOf("你\n\n\n好", 6))
        assertEquals(listOf("你", "好"), linesOf("你\r\n\r\n好", 6))
    }

    @Test
    fun `newlines at the ends add nothing`() {
        assertEquals(listOf("你好"), linesOf("\n你好", 6))
        assertEquals(listOf("你好"), linesOf("你好\n", 6))
        assertEquals(emptyList<String>(), linesOf("\n\n", 6))
        assertEquals(emptyList<List<Int>>(), rowsOf("", 6))
    }

    @Test
    fun `wrapping starts over after a break`() {
        assertEquals(listOf("你", "好", "世", "界"), linesOf("你好\n世界", 1))
    }

    @Test
    fun `a full row is flushed before the break`() {
        // The 3rd cell fills the row; the newline then has nothing to flush.
        assertEquals(listOf("你好世", "界"), linesOf("你好世\n界", 3))
    }
}

package com.pupil.hanzi.data

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The grid walks the characters and pulls a reading per han character. If
 * anything returns a different count the cells shift and the wrong syllable
 * lands under the wrong character -- which is how 行 showed up as háng.
 */
class ReadingAlignmentTest {

    private fun keysOf(text: String): List<String> =
        PinyinService.readings(text).map { it.selected.key }

    @Test
    fun `one reading per han character for a mixed sentence`() {
        val text = "南京市长参观了南京市长江大桥，重庆银行行长走过重庆银行长廊。"
        val readings = PinyinService.readings(text)
        val han = text.count { PinyinService.isHan(it) }
        assertEquals("reading count must equal the han count", han, readings.size)
    }

    @Test
    fun `readings line up with the characters in order`() {
        val text = "银行行长走了很长距离"
        val chars = PinyinService.readings(text).map { it.char }
        assertEquals("银行行长走了很长距离", chars.joinToString(""))
    }

    @Test
    fun `the reading under each character is the one the phrase supports`() {
        val text = "银行行长"
        assertEquals(listOf("yin2", "hang2", "xing2", "chang2"), keysOf(text))
    }

    @Test
    fun `punctuation does not shift the following characters`() {
        // 观 and 了 are followed by a comma; the characters after it must stay
        // in step.
        val text = "参观，了"
        val chars = PinyinService.readings(text).map { it.char }
        assertEquals(listOf('参', '观', '了'), chars)
    }

    @Test
    fun `a sentence mixing polyphones keeps both readings`() {
        val text = "南京市长过长江大桥"
        val keys = keysOf(text)
        // 南 京 市 长 过 长 江 大 桥 -- 长江 sits at index 5 and 6.
        assertEquals("chang2", keys[5])
        assertEquals("jiang1", keys[6])
    }
}

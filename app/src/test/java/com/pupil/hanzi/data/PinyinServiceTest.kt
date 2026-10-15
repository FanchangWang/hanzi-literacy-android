package com.pupil.hanzi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The point of using HanLP is context: the text is segmented and each word is
 * converted on its own. Converting the sentence in one call segments internally
 * but never re-reads the phrase dictionary, so 长江 comes out "zhǎng jiāng".
 *
 * These tests pin that behaviour, plus the key format the audio assets are
 * indexed by.
 */
class PinyinServiceTest {

    private fun keys(text: String): List<String> =
        PinyinService.readings(text).map { it.selected.key }

    private fun display(text: String): List<String> =
        PinyinService.readings(text).map { it.selected.display }

    @Test
    fun `a polyphone word uses its phrase reading`() {
        assertEquals(listOf("chong2", "fu4"), keys("重复"))
        assertEquals(listOf("yin2", "hang2"), keys("银行"))
    }

    @Test
    fun `context resolves across a longer sentence`() {
        // The case the user raised: 长江 must be cháng, not zhǎng.
        assertEquals(
            listOf("nan2", "jing1", "shi4", "chang2", "jiang1", "da4", "qiao2"),
            keys("南京市长江大桥"),
        )
    }

    @Test
    fun `the same character can read differently in one sentence`() {
        // 银行行长: the 行 of 银行 is háng, the 行 of 行长 is xíng, and the
        // 长 of 行长 is cháng -- HanLP reads 行长 as a word.
        assertEquals(listOf("yin2", "hang2", "xing2", "chang2"), keys("银行行长"))
    }

    @Test
    fun `a bare character is read as a single word`() {
        // HanLP looks a lone character up in the phrase dictionary first, so
        // 长 alone comes back as cháng rather than its commoner zhǎng. Both
        // reach the picker; which is preselected is cosmetic, not correctness.
        assertEquals(listOf("zhong4"), keys("重"))
        assertEquals(listOf("chang2"), keys("长"))
    }

    @Test
    fun `keys match the audio asset names`() {
        // The voice library is named "<letters><tone digit>"; the key must line
        // up exactly or playback silently falls back.
        for (k in keys("重复长江好重长一不")) {
            assertTrue("bad asset key: $k", k.matches(Regex("[a-z]+[1-5]")))
        }
    }

    @Test
    fun `display form carries the tone mark`() {
        assertEquals(listOf("chóng", "fù"), display("重复"))
        assertEquals(listOf("hǎo"), display("好"))
    }

    @Test
    fun `initial and final always rebuild the display`() {
        for (text in listOf("重复", "长江大桥", "银行行长", "南京市长江大桥", "好", "懂")) {
            for (r in PinyinService.readings(text)) {
                assertEquals(
                    "split mismatch for ${r.selected.key}",
                    r.selected.display,
                    r.selected.initial + r.selected.final,
                )
            }
        }
    }

    @Test
    fun `alternates never include the chosen reading`() {
        val zhong = PinyinService.readings("重").first()
        assertEquals("zhong4", zhong.selected.key)
        assertTrue(
            "chong2 should be offered as an alternate: ${zhong.alternates.map { it.key }}",
            zhong.alternates.any { it.key == "chong2" },
        )
        assertTrue(
            "alternates must exclude the chosen one",
            zhong.alternates.none { it.key == zhong.selected.key },
        )
    }

    @Test
    fun `a polyphone reports more than one reading`() {
        assertTrue(PinyinService.readings("重").first().alternates.isNotEmpty())
        assertTrue(
            "懂 is not a polyphone",
            PinyinService.readings("懂").first().alternates.isEmpty(),
        )
    }

    @Test
    fun `punctuation produces no readings`() {
        assertTrue(PinyinService.readings("，").isEmpty())
        assertTrue(PinyinService.readings("A").isEmpty())
    }

    @Test
    fun `han detection covers the stroke data ranges`() {
        assertTrue(PinyinService.isHan('好'))
        assertTrue(PinyinService.isHan('㐀'))   // Extension A
        assertTrue(PinyinService.isHan('⺀'))   // Radicals Supplement
        assertTrue(!PinyinService.isHan('，'))
        assertTrue(!PinyinService.isHan('a'))
        assertTrue(!PinyinService.isHan('1'))
    }

    @Test
    fun `a sentence yields one reading per han character`() {
        val text = "南京市长江大桥上有一个人"
        assertEquals(
            text.count { PinyinService.isHan(it) },
            PinyinService.readings(text).size,
        )
    }

    @Test
    fun `the same character in one sentence keeps its own reading`() {
        // The reported case. HanLP reads 行长 as a word, so its 长 is cháng,
        // while 长江 is also cháng but 市长 in this segmentation comes out as a
        // bare 长 and takes the dictionary's first reading. What matters is
        // that the readings are returned per POSITION, not merged per
        // character: 银行 and 行长 must keep different readings for the two 行.
        val sentence = "重庆银行行长走了很长距离"
        val readings = PinyinService.readings(sentence)
        assertEquals(
            sentence.count { PinyinService.isHan(it) },
            readings.size,
        )
        val keys = readings.map { it.selected.key }
        // 重庆 -> chong2 qing4, 银行 -> yin2 hang2, 行长 -> xing2 chang2
        assertEquals("hang2", keys[3])
        assertEquals("xing2", keys[4])
        assertEquals("chang2", keys[5])
    }

    @Test
    fun `readings are not merged across positions of the same character`() {
        val keys = PinyinService.readings("银行行长").map { it.selected.key }
        assertEquals(listOf("yin2", "hang2", "xing2", "chang2"), keys)
        // The two 行 must not collapse into one entry.
        assertEquals("hang2", keys[1])
        assertEquals("xing2", keys[2])
    }
}

package com.pupil.hanzi.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks in the four tone-marking rules. Every case here corresponds to a real
 * bug that an earlier implementation of this file actually had.
 *
 * Expectations were checked against the dictionary's actual output, not
 * guessed -- see the comment on the tests that were originally wrong.
 */
class PinyinFormatterTest {

    @Test
    fun `tone goes on the nucleus not the first vowel`() {
        // Rule 1. Marking the first vowel gives yǒou / wùei / jìao.
        assertEquals("yǒu", PinyinFormatter.format("you3"))
        assertEquals("wèi", PinyinFormatter.format("wei4"))
        assertEquals("jiāo", PinyinFormatter.format("jiao1"))
        assertEquals("shuǐ", PinyinFormatter.format("shui3"))
        assertEquals("guī", PinyinFormatter.format("gui1"))
    }

    @Test
    fun `nucleus priority is a then o then e`() {
        // xiong: 'o' comes first, and o wins over i.
        assertEquals("xióng", PinyinFormatter.format("xiong2"))
        // hao: 'a' wins over o.
        assertEquals("hǎo", PinyinFormatter.format("hao3"))
        // duō: o is the only vowel.
        assertEquals("duō", PinyinFormatter.format("duo1"))
    }

    @Test
    fun `n and m are not vowels inside a final`() {
        // Rule 2. 'e' is the nucleus here, not the trailing n.
        assertEquals("ēn", PinyinFormatter.format("en1"))
        assertEquals("ēng", PinyinFormatter.format("eng1"))
        assertEquals("ān", PinyinFormatter.format("an1"))
        assertEquals("āng", PinyinFormatter.format("ang1"))
    }

    @Test
    fun `n as a whole final carries its tone`() {
        // 嗯 has real toned forms: ń ň ǹ.
        assertEquals("ǹ", PinyinFormatter.format("n4"))
        assertEquals("ń", PinyinFormatter.format("n2"))
        // But neutral tone leaves it bare.
        assertEquals("n", PinyinFormatter.format("n5"))
    }

    @Test
    fun `u after y stays plain u`() {
        // Rule 4. Not yüán.
        assertEquals("yuán", PinyinFormatter.format("yuan2"))
        assertEquals("yíng", PinyinFormatter.format("ying2"))
        assertEquals("yú", PinyinFormatter.format("yu2"))
        assertEquals("yǒng", PinyinFormatter.format("yong3"))
    }

    @Test
    fun `v means u umlaut and renders precomposed`() {
        // Rule 3. Composing from u + U+0308 would yield "nv" with no dots.
        assertEquals("nǚ", PinyinFormatter.format("nv3"))
        assertEquals("lǜ", PinyinFormatter.format("lv4"))
        assertEquals("lüè", PinyinFormatter.format("lve4"))
        // Precomposed: U+01DA, and no bare combining diaeresis.
        assertEquals(0x01DA, PinyinFormatter.format("nv3").last().code)
        assertTrue(PinyinFormatter.format("nv3").none { it.code == 0x0308 })
    }

    @Test
    fun `textbook forms round trip`() {
        val cases = mapOf(
            "hao3" to "hǎo", "zhong1" to "zhōng", "chong2" to "chóng",
            "yi4" to "yì", "er2" to "ér", "wu2" to "wú", "you3" to "yǒu",
            "wei4" to "wèi", "weng1" to "wēng", "zhi1" to "zhī", "shi4" to "shì",
            "qu2" to "qú", "jue2" to "jué", "xue2" to "xué", "yue4" to "yuè",
            "niu2" to "niú", "kuai4" to "kuài", "zhui1" to "zhuī", "hua2" to "huá",
            "shuang1" to "shuāng", "cui4" to "cuì", "zhuang1" to "zhuāng",
            "qiong2" to "qióng", "jun1" to "jūn", "ye4" to "yè", "yě" to "yě",
        )
        cases.forEach { (input, expected) ->
            assertEquals("format($input)", expected, PinyinFormatter.format(input))
        }
    }

    @Test
    fun `neutral tone has no mark`() {
        assertEquals("de", PinyinFormatter.format("de5"))
        assertEquals("ma", PinyinFormatter.format("ma5"))
        assertEquals("zhong", PinyinFormatter.format("zhong5"))
        // v is normalised to ü even without a tone.
        assertEquals("lü", PinyinFormatter.format("lv5"))
    }

    @Test
    fun `malformed input is passed through not thrown`() {
        assertEquals("hao", PinyinFormatter.format("hao"))
        assertEquals("hao9", PinyinFormatter.format("hao9"))
        assertEquals("hao0", PinyinFormatter.format("hao0"))
        assertEquals("", PinyinFormatter.format(""))
        assertEquals("3", PinyinFormatter.format("3"))
    }

    @Test
    fun `every reading of common chars renders and splits cleanly`() {
        val chars = "好重长行人一不和水火天地你我他门山出月日风雨电都还少"
        for (c in chars) {
            val readings = PinyinService.readingsOf(c)
            assertTrue("no readings for $c", readings.isNotEmpty())
            for (r in readings) {
                assertTrue("empty display for $c/$r", r.display.isNotEmpty())
                assertEquals(
                    "initial+final must rebuild display for $c/$r",
                    r.display, r.initial + r.final,
                )
                // Never leak a bare combining mark onto the UI.
                assertTrue(
                    "combining mark leaked: ${r.display}",
                    r.display.none { it.code in 0x0300..0x036F },
                )
            }
        }
    }
}

class PinyinServiceTest {

    @Test
    fun `polyphone exposes every reading`() {
        val keys = PinyinService.readingsOf('重').map { it.key }
        assertTrue("expected chong2 among $keys", keys.contains("chong2"))
        assertTrue("expected zhong4 among $keys", keys.contains("zhong4"))
        assertEquals("readings must be distinct", keys.size, keys.distinct().size)
    }

    @Test
    fun `initial is split as plain letters`() {
        val zhong = PinyinService.readingsOf('中').first { it.key == "zhong1" }
        assertEquals("zh", zhong.initial)
        assertEquals("ōng", zhong.final)
    }

    @Test
    fun `y and w count as the coloured initial`() {
        // They are orthography, but the two-tone display still needs a split.
        val yi = PinyinService.readingsOf('一').first { it.key == "yi1" }
        assertEquals("y", yi.initial)
        assertEquals("ī", yi.final)

        val wu = PinyinService.readingsOf('五').first { it.key == "wu3" }
        assertEquals("w", wu.initial)
        assertEquals("ǔ", wu.final)
    }

    @Test
    fun `punctuation and latin have no readings`() {
        // The dictionary echoes the input back for these; we must not.
        assertTrue(PinyinService.readingsOf('，').isEmpty())
        assertTrue(PinyinService.readingsOf('.').isEmpty())
        assertTrue(PinyinService.readingsOf('A').isEmpty())
        assertTrue(PinyinService.readingsOf(' ').isEmpty())
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
    fun `phrase reading beats per character for a known word`() {
        // 重复 must be chong2 fu4; the per-character default would be zhong4.
        assertEquals(listOf("chong2", "fu4"), PinyinService.phraseKeys("重复"))
        // 长江 must stay chang2 even though 长 alone defaults to zhang3.
        assertEquals(listOf("chang2", "jiang1"), PinyinService.phraseKeys("长江"))
    }

    @Test
    fun `phrase skips punctuation`() {
        val keys = PinyinService.phraseKeys("好，天")
        assertEquals(2, keys.size)
        assertTrue(keys.contains("hao3"))
        assertTrue(keys.contains("tian1"))
    }
}
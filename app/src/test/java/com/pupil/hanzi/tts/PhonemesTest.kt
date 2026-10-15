package com.pupil.hanzi.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The lexicon we generate is the only thing between the model and a wrong
 * reading. Every expectation here was read off the shipped lexicon.txt of
 * zh_CN-xiao_ya-medium, because several obvious guesses are wrong:
 *
 *     女 -> n v 3     略 -> l ve 4      u after a consonant is umlaut
 *     冤 -> y uan 1   元 -> y uan 2      ...but after y it stays u
 *     水 -> sh ui 3   快 -> k uai 4      ...and uai keeps its u
 *     嗯 -> Ø n 2     鹅 -> Ø e 2       no initial, marked with Ø
 *     儿 -> Ø er 2    爱 -> Ø ai 4      every bare vowel takes Ø
 *     有 -> y ou 3    为 -> w ei 2       y and w keep the final as written
 */
class PhonemesTest {

    @Test
    fun `ordinary syllables split into initial final tone`() {
        assertEquals(listOf("ch", "ang", "2"), Phonemes.of("chang2"))
        assertEquals(listOf("zh", "ong", "1"), Phonemes.of("zhong1"))
        assertEquals(listOf("h", "ao", "3"), Phonemes.of("hao3"))
        assertEquals(listOf("sh", "ui", "3"), Phonemes.of("shui3"))
        assertEquals(listOf("k", "uai", "4"), Phonemes.of("kuai4"))
        assertEquals(listOf("n", "a", "4"), Phonemes.of("na4"))
        assertEquals(listOf("l", "e", "4"), Phonemes.of("le4"))
    }

    @Test
    fun `u umlaut is v after a consonant`() {
        assertEquals(listOf("n", "v", "3"), Phonemes.of("nv3"))
        assertEquals(listOf("l", "ve", "4"), Phonemes.of("lve4"))
    }

    @Test
    fun `after y the model spells u not v`() {
        // 冤 is y uan 1, 元 is y uan 2: the model has no van token.
        assertEquals(listOf("y", "uan", "2"), Phonemes.of("yuan2"))
        assertEquals(listOf("y", "uan", "3"), Phonemes.of("yuan3"))
        assertEquals(listOf("y", "ue", "4"), Phonemes.of("yue4"))
        assertEquals(listOf("y", "ong", "3"), Phonemes.of("yong3"))
        assertEquals(listOf("y", "in", "2"), Phonemes.of("yin2"))
        assertEquals(listOf("y", "ou", "3"), Phonemes.of("you3"))
    }

    @Test
    fun `w behaves like y`() {
        // 为 is w ei 2, 问 is w en 4, 我 is w o 3: no contraction.
        assertEquals(listOf("w", "ei", "4"), Phonemes.of("wei4"))
        assertEquals(listOf("w", "en", "1"), Phonemes.of("wen1"))
        assertEquals(listOf("w", "o", "3"), Phonemes.of("wo3"))
        assertEquals(listOf("w", "u", "3"), Phonemes.of("wu3"))
    }

    @Test
    fun `bare finals are marked with the zero symbol`() {
        // 嗯 -> Ø n 2, 鹅 -> Ø e 2, 儿 -> Ø er 2, 爱 -> Ø ai 4.
        assertEquals(listOf("Ø", "n", "2"), Phonemes.of("n2"))
        assertEquals(listOf("Ø", "m", "2"), Phonemes.of("m2"))
        assertEquals(listOf("Ø", "er", "2"), Phonemes.of("er2"))
        assertEquals(listOf("Ø", "e", "2"), Phonemes.of("e2"))
        assertEquals(listOf("Ø", "a", "4"), Phonemes.of("a4"))
        assertEquals(listOf("Ø", "ai", "4"), Phonemes.of("ai4"))
        assertEquals(listOf("Ø", "an", "1"), Phonemes.of("an1"))
    }

    @Test
    fun `every token we emit exists in the model`() {
        val keys = listOf(
            "chang2", "zhong1", "chong2", "hao3", "hao4", "shui3", "kuai4",
            "wei4", "wen1", "you3", "yu2", "nv3", "lve4", "er2", "yi1", "wu3",
            "zhuang1", "qiong2", "jun1", "xiong2", "n2", "a4", "e2", "ai4",
            "zhui1", "shuang1", "cui4", "feng1", "duo1", "jiao1", "niu2",
            "yuan2", "yue4", "na4", "chun2", "shang1", "er4", "le4", "wo3",
        )
        for (k in keys) {
            val ph = Phonemes.of(k)
            assertTrue("no phonemes for $k", ph != null)
            for (t in ph!!) {
                assertTrue("model has no token '$t' (from $k -> $ph)", t in Phonemes.ALLOWED)
            }
        }
    }

    @Test
    fun `malformed keys return null rather than a wrong reading`() {
        assertNull(Phonemes.of(""))
        assertNull(Phonemes.of("a"))        // no tone digit
        assertNull(Phonemes.of("l4"))       // l needs a final
        assertNull(Phonemes.of("hao"))      // no tone digit
        assertNull(Phonemes.of("hao9"))     // tone out of range
        assertNull(Phonemes.of("hao0"))
    }

    @Test
    fun `a phrase line spells every character with a boundary`() {
        // The user case: 南京市长江大桥, checked character by character
        // against the shipped lexicon.
        val line = Phonemes.line(
            "南京市长江大桥",
            listOf("nan2", "jing1", "shi4", "chang2", "jiang1", "da4", "qiao2"),
        )
        assertEquals(
            "南京市长江大桥 n an 2 _ j ing 1 _ sh i 4 _ ch ang 2 _ " +
                "j iang 1 _ d a 4 _ q iao 2 _",
            line,
        )
    }

    @Test
    fun `punctuation is passed through so a sentence keeps its stops`() {
        // Punctuation has no pinyin key, so it still occupies a slot.
        val line = Phonemes.line("好，", listOf("hao3", ""))
        assertTrue("comma should survive: $line", line != null && line.contains("，"))
    }

    @Test
    fun `a line is refused when text and keys disagree`() {
        assertNull(Phonemes.line("好", listOf("hao3", "hao4")))
        assertNull(Phonemes.line("好", emptyList()))
    }

    @Test
    fun `a key that cannot be expressed refuses the whole line`() {
        // Better to return null and fall back to the syllable library than to
        // write a line the model would mispronounce.
        assertNull(Phonemes.line("好", listOf("hao9", "")))
    }

    @Test
    fun `latin and digits are refused rather than guessed`() {
        assertNull(Phonemes.line("A", listOf("a1")))
        assertNull(Phonemes.line("1", listOf("yi1")))
    }
}

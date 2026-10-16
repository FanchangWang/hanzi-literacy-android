package com.pupil.hanzi.data

import com.hankcs.hanlp.dictionary.py.Pinyin
import com.hankcs.hanlp.dictionary.py.PinyinDictionary
import com.hankcs.hanlp.seg.common.Term
import com.pupil.hanzi.nlp.Segmenters

/** One reading of one character, e.g. "好" -> hǎo / hào. */
data class PinyinReading(
    /** Audio asset name, "hao3". Doubles as the dictionary key. */
    val key: String,
    /** Display form with the tone mark, "hǎo". */
    val display: String,
    /** Initial, drawn in red, e.g. "h". */
    val initial: String,
    /** Final, drawn in blue, e.g. "ǎo". */
    val final: String,
)

/** One character of the input with the reading chosen for it. */
data class CharReading(
    val char: Char,
    val selected: PinyinReading,
    /** The character's other readings, for the polyphone picker. */
    val alternates: List<PinyinReading>,
)

/**
 * Character -> pinyin, using HanLP's dictionary and segmenter.
 *
 * The order of the two steps is the whole point. Converting the sentence in a
 * single call segments internally but never re-reads the phrase dictionary, so
 * it still returns zhǎng for 长 in 长江. Converting each *segmented word* gives
 * the right answer: 南京市 / 长江 / 大桥, and 长江 is then looked up as a unit.
 *
 * The second invariant is that this returns exactly one [CharReading] per
 * han character, in order. Callers walk the text pulling a reading per
 * character, so a single dropped or duplicated entry shifts every syllable
 * after it -- which showed up as 行 carrying 银's reading.
 */
object PinyinService {

    /** CJK ranges that the stroke data covers. */
    fun isHan(char: Char): Boolean {
        val c = char.code
        return c in 0x4E00..0x9FFF ||     // CJK Unified
            c in 0x3400..0x4DBF ||         // Extension A
            c in 0x2E80..0x2EFF ||         // Radicals Supplement
            c in 0xF900..0xFAFF            // Compatibility Ideographs
    }

    /**
     * One [CharReading] per han character of [text], in order. Punctuation and
     * anything else yields nothing.
     */
    fun readings(text: String): List<CharReading> {
        val out = ArrayList<CharReading>(text.length)
        for (term in Segmenters.segment(text)) {
            out += readingsForTerm(term)
        }
        // HanLP can drop or reshape a segment; pad or trim so the caller can
        // index this in step with the text.
        val han = text.filter { isHan(it) }
        if (out.size == han.length) return out
        if (out.size > han.length) return out.take(han.length)
        val rebuilt = ArrayList<CharReading>(han.length)
        var cursor = 0
        for (c in han) {
            val r = out.getOrNull(cursor)
            if (r != null && r.char == c) {
                rebuilt += r
            } else {
                // Recover the character's own first reading so the slot is
                // never empty and the remaining ones stay in place.
                val fb = firstOf(c) ?: continue
                rebuilt += CharReading(c, toReading(fb), allOf(c).filter { it.key != fb.let { keyOf(it) } })
            }
            cursor++
        }
        return rebuilt
    }



    /**
     * Readings for one segmented word, one entry per han character.
     *
     * The phrase reading decides the primary; each character also keeps its own
     * dictionary readings as alternates, minus the one already chosen, so the
     * picker does not offer a duplicate.
     */
    private fun readingsForTerm(term: Term): List<CharReading> {
        val word = term.word
        if (word.isEmpty()) return emptyList()

        val phrase = if (isHan(word[0])) {
            PinyinDictionary.convertToPinyin(word, true)
        } else {
            emptyList()
        }

        val out = ArrayList<CharReading>(word.length)
        for ((i, ch) in word.withIndex()) {
            if (!isHan(ch)) continue
            val chosen = phrase.getOrNull(i) ?: firstOf(ch) ?: continue
            val chosenKey = keyOf(chosen)
            val chosenReading = toReading(chosen)
            out += CharReading(
                char = ch,
                selected = chosenReading,
                alternates = allOf(ch).filter { it.key != chosenKey },
            )
        }
        return out
    }

    private fun firstOf(char: Char): Pinyin? =
        PinyinDictionary.get(char.toString())?.firstOrNull()

    private fun allOf(char: Char): List<PinyinReading> =
        PinyinDictionary.get(char.toString())?.map { toReading(it) }.orEmpty()

    private fun keyOf(p: Pinyin): String =
        p.getPinyinWithoutTone() + (if (p.getTone() > 0) p.getTone().toString() else "5")

    private fun toReading(p: Pinyin): PinyinReading {
        val display = p.getPinyinWithToneMark()
        val head = p.getHeadString()
        return PinyinReading(
            key = keyOf(p),
            display = display,
            initial = if (head.isEmpty()) "" else display.substring(0, head.length),
            final = if (head.isEmpty()) display else display.substring(head.length),
        )
    }
}

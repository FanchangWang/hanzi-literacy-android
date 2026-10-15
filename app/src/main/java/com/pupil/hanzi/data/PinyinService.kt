package com.pupil.hanzi.data

import com.hankcs.hanlp.HanLP
import com.hankcs.hanlp.dictionary.py.Pinyin
import com.hankcs.hanlp.dictionary.py.PinyinDictionary
import com.hankcs.hanlp.seg.common.Term

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
) {
    val hasAlternates: Boolean get() = false
}

/** One character of the input with the reading that was chosen for it. */
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
 * 长江 comes out as "zhǎng jiāng". Converting each *segmented word* instead
 * gives "cháng jiāng", because the word is then looked up as a unit:
 *
 *     HanLP.segment("南京市长江大桥") -> 南京市 / 长江 / 大桥
 *     convertToPinyin("长江")        -> cháng / jiāng
 *
 * Every character also keeps its own dictionary readings as alternates, so the
 * picker can still switch 重 between zhòng and chóng.
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
     * One [CharReading] per character of [text], in order. Punctuation and
     * other non-hanzi produce no entry; the caller filters by [isHan].
     */
    fun readings(text: String): List<CharReading> {
        val out = ArrayList<CharReading>(text.length)
        for (term in HanLP.segment(text)) {
            out += readingsForTerm(term)
        }
        return out
    }

    /**
     * The chosen key per character, in order. This is what the audio and the
     * sherpa lexicon are indexed by.
     */
    fun defaultKeys(text: String): List<String> = readings(text).map { it.selected.key }

    /**
     * Readings for one segmented word.
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

        return word.mapIndexedNotNull { i, ch ->
            if (!isHan(ch)) return@mapIndexedNotNull null
            val chosen = phrase.getOrNull(i) ?: firstOf(ch) ?: return@mapIndexedNotNull null
            val chosenReading = toReading(chosen)
            val alternates = allOf(ch).filter { it.key != chosenReading.key }
            CharReading(char = ch, selected = chosenReading, alternates = alternates)
        }
    }

    private fun firstOf(char: Char): Pinyin? = PinyinDictionary.get(char.toString())?.firstOrNull()

    private fun allOf(char: Char): List<PinyinReading> =
        PinyinDictionary.get(char.toString())
            ?.map { toReading(it) }
            .orEmpty()

    private fun toReading(p: Pinyin): PinyinReading {
        val display = p.getPinyinWithToneMark()
        val head = p.getHeadString()
        return PinyinReading(
            // "cháng" -> "chang2"; an unmarked syllable is treated as neutral,
            // which matches the dictionary's own convention.
            key = p.getPinyinWithoutTone() + (if (p.getTone() > 0) p.getTone().toString() else "5"),
            display = display,
            initial = if (head.isEmpty()) "" else display.substring(0, head.length),
            final = if (head.isEmpty()) display else display.substring(head.length),
        )
    }
}

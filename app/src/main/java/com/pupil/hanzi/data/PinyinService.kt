package com.pupil.hanzi.data

import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum
import com.github.houbb.pinyin.util.PinyinHelper

/**
 * Converts the flat pinyin strings that come out of the dictionary
 * ("chong2") into what we actually display ("chóng").
 *
 * Four rules, each of which exists because the obvious implementation gets
 * it wrong. They are covered by PinyinServiceTest.
 *
 *  1. The tone mark goes on the syllable's *nucleus*, not its first vowel.
 *     you3 -> yǒu, wei4 -> wèi, jiao1 -> jiāo.
 *  2. Nucleus priority is a > o > e; otherwise the last true vowel.
 *     n/m/ê only count when they are the whole final.
 *  3. Always emit precomposed codepoints. "nǚ" is n + U+01DA; building it
 *     from n + u + U+0308 loses the umlaut dots.
 *  4. y and w are orthography, not initials. What follows them is already a
 *     complete final, and a u after y is plain u (yuán, never yüán).
 */
object PinyinFormatter {

    /** Tone-marked vowel forms, indexed by tone digit 1..5 (5 = neutral). */
    private val TONE_VOWELS: Map<Char, List<Char>> = mapOf(
        'a' to listOf('ā', 'á', 'ǎ', 'à', 'a'),
        'o' to listOf('ō', 'ó', 'ǒ', 'ò', 'o'),
        'e' to listOf('ē', 'é', 'ě', 'è', 'e'),
        'i' to listOf('ī', 'í', 'ǐ', 'ì', 'i'),
        'u' to listOf('ū', 'ú', 'ǔ', 'ù', 'u'),
        'ü' to listOf('ǖ', 'ǘ', 'ǚ', 'ǜ', 'ü'),
        'n' to listOf('n', 'n', 'ň', 'n', 'n'),
        'm' to listOf('m', 'm', 'm', 'm', 'm'),
        'ê' to listOf('ê', 'ế', 'ê', 'ề', 'ê'),
        // 嗯 has real 2nd/3rd/4th tone forms (ń / ň / ǹ).
        'n' to listOf('n', 'ń', 'ň', 'ǹ', 'n'),
    )

    /** True vowels, i.e. the ones that can carry a tone inside a final. */
    private const val TRUE_VOWELS = "aoeiuü"

    private val INITIALS = listOf(
        "zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l",
        "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w",
    ).sortedByDescending { it.length }

    /**
     * "chong2" -> "chóng". Returns [input] unchanged if it is malformed
     * (no trailing tone digit, or a digit outside 1..5) so the UI can render
     * something rather than throw.
     */
    fun format(syllable: String): String {
        if (syllable.isEmpty()) return syllable
        val toneDigit = syllable.last()
        if (toneDigit !in '1'..'5') return syllable
        val body = syllable.dropLast(1)
        if (body.isEmpty()) return syllable

        // Syllabic nasals are finals, not initials: "n4" is 嗯, not n + "".
        // "ng2" (嗯 variant) likewise. Check these before the initial table,
        // which would otherwise swallow the n as a normal initial.
        if (body == "n" || body == "m" || body == "ng" || body == "hm") {
            return markFinal(body, toneDigit - '1')
        }

        val initial = INITIALS.firstOrNull { body.startsWith(it) } ?: ""
        val finalPart = body.substring(initial.length)
        return initial + markFinal(finalPart, toneDigit - '1')
    }

    private fun markFinal(finalPart: String, toneIndex: Int): String {
        // 'v' stands for ü in the dictionary's notation.
        val f = finalPart.replace('v', 'ü')
        if (f.isEmpty()) return f

        // Rule 2: a > o > e wins outright.
        for (v in "aoe") {
            val i = f.indexOf(v)
            if (i >= 0) {
                return f.substring(0, i) + TONE_VOWELS.getValue(v)[toneIndex] + f.substring(i + 1)
            }
        }

        // Otherwise the last true vowel.
        val idx = f.indexOfLast { it in TRUE_VOWELS }
        if (idx >= 0) {
            return f.substring(0, idx) + TONE_VOWELS.getValue(f[idx])[toneIndex] + f.substring(idx + 1)
        }

        // n / m / ê as the whole final.
        val table = TONE_VOWELS[f[0]]
        return if (table != null) {
            table[toneIndex] + f.substring(1)
        } else {
            f
        }
    }
}

/** One reading of one character, e.g. "好" -> hǎo / hào. */
data class PinyinReading(
    /** Dictionary key, "hao3". Doubles as the audio asset name. */
    val key: String,
    /** Display form, "hǎo". */
    val display: String,
    /** Initial red, e.g. "h". */
    val initial: String,
    /** Final blue, e.g. "ǎo". */
    val final: String,
)

/**
 * Character -> pinyin lookups.
 *
 * The dictionary returns every reading a character has, including historical
 * ones ("一" -> yi1 yi2 yi4). Some of those have no matching audio asset,
 * so callers must be prepared for a missing mp3; see VoiceAssets.
 */
object PinyinService {

    /**
     * All readings for [char], dictionary order preserved, de-duplicated by
     * key. Empty for non-Chinese characters (punctuation, latin).
     */
    fun readingsOf(char: Char): List<PinyinReading> {
        if (!isHan(char)) return emptyList()
        return rawKeys(char)
            .distinct()
            // Non-Chinese input makes the dictionary echo the character back,
            // which is not a reading.
            .filter { it.isNotEmpty() && it.last() in '1'..'5' }
            .map { key ->
                splitInitialFinal(PinyinFormatter.format(key), key)
            }
    }

    /**
     * Raw dictionary keys, e.g. "重" -> ["zhong4", "chong2", "tong2"].
     * These double as the audio asset names.
     */
    fun rawKeys(char: Char): List<String> =
        PinyinHelper.toPinyinList(char, PinyinStyleEnum.NUM_LAST)


    /**
     * The dictionary key the dictionary itself would pick for [char] when it
     * appears alone: its first reading.
     */
    fun defaultKey(char: Char): String? = rawKeys(char).firstOrNull()

    /**
     * Dictionary keys for [text] as a phrase, preferring the word reading
     * where the dictionary knows one: "重复" -> chong2 fu4, not zhong4 fu4.
     *
     * The dictionary returns plain syllables separated by spaces, so a token
     * is valid when it ends in a tone digit -- not when its first character is
     * han, since a syllable like "chong2" is obviously not a han character.
     */
    fun phraseKeys(text: String): List<String> {
        val phrase = PinyinHelper.toPinyin(text, PinyinStyleEnum.NUM_LAST)
            .split(' ', ',', '\u3000')
            .map { it.trim() }
            .filter { it.length > 1 && it.last() in '1'..'5' && it[it.length - 2].isLetter() }
        val perChar = text.filter { isHan(it) }.map { char ->
            rawKeys(char).firstOrNull { it.last() in '1'..'5' } ?: return emptyList()
        }
        // The dictionary does not always align with per-character output;
        // only trust it when the counts match.
        return if (phrase.size == perChar.size) phrase else perChar
    }

    private fun splitInitialFinal(display: String, key: String): PinyinReading {
        val initial = rawInitialOf(key)
        val initialDisplay = if (initial.isEmpty()) "" else display.substring(0, initial.length)
        val finalDisplay = display.substring(initialDisplay.length)
        return PinyinReading(
            key = key,
            display = display,
            initial = initialDisplay,
            final = finalDisplay,
        )
    }

    /**
     * The initial as plain letters ("zh"), taken from the flat key so we
     * never mis-split a tone-marked display string.
     */
    private fun rawInitialOf(key: String): String {
        val body = key.dropLast(1)
        return INITIALS_FOR_SPLIT.firstOrNull { body.startsWith(it) } ?: ""
    }

    private val INITIALS_FOR_SPLIT = listOf(
        "zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l",
        "g", "k", "h", "j", "q", "x", "r", "z", "c", "s", "y", "w",
    ).sortedByDescending { it.length }

    /** CJK ranges that the stroke data covers. */
    fun isHan(char: Char): Boolean {
        val c = char.code
        return c in 0x4E00..0x9FFF ||     // CJK Unified
            c in 0x3400..0x4DBF ||         // Extension A
            c in 0x2E80..0x2EFF ||         // Radicals Supplement
            c in 0xF900..0xFAFF            // Compatibility Ideographs
    }
}
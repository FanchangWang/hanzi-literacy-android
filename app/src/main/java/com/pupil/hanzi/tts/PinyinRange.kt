package com.pupil.hanzi.tts

/**
 * The CJK ranges the stroke data covers.
 *
 * Duplicated from PinyinService rather than imported so this package has no
 * dependency on the pinyin layer: the TTS path must work even if the pinyin
 * source is swapped out again.
 */
object PinyinRange {
    fun isHan(char: Char): Boolean {
        val c = char.code
        return c in 0x4E00..0x9FFF ||     // CJK Unified
            c in 0x3400..0x4DBF ||         // Extension A
            c in 0x2E80..0x2EFF ||         // Radicals Supplement
            c in 0xF900..0xFAFF            // Compatibility Ideographs
    }
}

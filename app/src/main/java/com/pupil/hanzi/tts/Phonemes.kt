package com.pupil.hanzi.tts

/**
 * Converts an app pinyin key ("chang2") into the phoneme tokens the xiao_ya
 * model expects ("n an 2" style: "n" "an" "2").
 *
 * The model's tokens.txt holds 85 entries, all phonemic. There is no hanzi
 * token at all, which is why a lexicon is mandatory -- and why one we generate
 * ourselves is enough, since the model has nothing else to fall back on.
 *
 * Every rule below was read off the shipped lexicon rather than derived,
 * because several obvious guesses are wrong:
 *
 *     女 -> n v 3     略 -> l ve 4      u after a consonant is umlaut
 *     冤 -> y uan 1   元 -> y uan 2      ...but after y it stays u
 *     水 -> sh ui 3   快 -> k uai 4      ...and uai keeps its u
 *     嗯 -> Ø n 2     鹅 -> Ø e 2       no initial, marked with Ø
 *     儿 -> Ø er 2    爱 -> Ø ai 4      every bare vowel takes Ø
 *     有 -> y ou 3    为 -> w ei 2       y and w keep the final as written
 */
object Phonemes {

    /** Word boundary between characters inside a phrase. */
    const val WORD = "_"

    /** Marks a syllable that has no consonant initial. */
    const val ZERO = "Ø"

    /** Consonant initials, longest first so zh wins over z. */
    private val INITIALS = listOf(
        "zh", "ch", "sh", "b", "p", "m", "f", "d", "t", "n", "l",
        "g", "k", "h", "j", "q", "x", "r", "z", "c", "s",
    ).sortedByDescending { it.length }

    /** Finals the model lists as one token, longest first. */
    private val COMPOUND_FINALS = listOf(
        "iang", "iong", "uang", "ueng", "iao", "ian", "ing", "uai", "uan",
        "ang", "eng", "ong", "ai", "ei", "ao", "ou", "an", "en",
        "ia", "ie", "iu", "in", "ua", "uo", "ui", "un", "ve", "ue",
    ).sortedByDescending { it.length }

    /**
     * Syllables that look like they have an initial but do not: the n of 嗯, the
     * m of 鹅 and the er of 儿 are the whole final, and the model marks them
     * with Ø rather than splitting them.
     */
    private val SYLLABIC = setOf("n", "m", "er", "ng", "ê")

    /**
     * Finals whose leading u is a plain u rather than umlaut. `uai` belongs
     * here even though it starts with u.
     */
    private val PLAIN_U_FINALS = setOf(
        "u", "ua", "uo", "ui", "un", "uai", "uan", "uang", "ueng",
    )

    /** Punctuation the model knows, passed through untouched. */
    private val PUNCTUATION: Set<Char> = setOf(
        '。', '.', '？', '?', '！', '!', '—', '…', '、', '，', ',',
        '：', ':', '；', ';', ' ',
    )

    /** Every token the model knows; a lexicon may not contain anything else. */
    val ALLOWED: Set<String> by lazy {
        buildSet {
            add(WORD)
            add("^")
            add("$")
            add(ZERO)
            addAll(INITIALS)
            add("y")
            add("w")
            add("a"); add("o"); add("e"); add("i"); add("u"); add("v")
            add("er"); add("n"); add("m")
            addAll(COMPOUND_FINALS)
            addAll(listOf("1", "2", "3", "4", "5"))
            addAll(PUNCTUATION.map { it.toString() })
        }
    }

    /**
     * "chang2" -> ["ch", "ang", "2"]; "er2" -> ["Ø", "er", "2"].
     *
     * Returns null when the key is malformed or would need a token the model
     * does not have, so the caller falls back to the syllable library rather
     * than emitting a line that would be mispronounced.
     */
    fun of(key: String): List<String>? {
        if (key.length < 2) return null
        val tone = key.last()
        if (tone !in '1'..'5') return null
        val body = key.dropLast(1)
        if (body.isEmpty()) return null
        val t = tone.toString()

        // The final is the whole syllable, so it must not be split.
        if (body in SYLLABIC) return listOf(ZERO, body, t)

        // y and w are initials; the final is left exactly as written.
        if (body[0] == 'y' || body[0] == 'w') {
            val rest = body.drop(1)
            return when {
                rest.isEmpty() -> null
                rest in COMPOUND_FINALS || rest.length == 1 ->
                    listOf(body[0].toString(), rest, t)
                else -> null
            }
        }

        val initial = INITIALS.firstOrNull { body.startsWith(it) }
            ?: return if (body in COMPOUND_FINALS || body.length == 1) {
                // A bare vowel: 爱 -> Ø ai 4, 安 -> Ø an 1.
                listOf(ZERO, body, t)
            } else {
                null
            }

        val rest = body.drop(initial.length)
        if (rest.isEmpty()) return null
        // A leading u is umlaut only when the final is not one of the plain
        // u finals: 女 -> n v, but 水 -> sh ui and 快 -> k uai.
        val finalPart = if (rest.startsWith("u") && rest !in PLAIN_U_FINALS) {
            "v" + rest.drop(1)
        } else {
            rest
        }
        return if (finalPart in COMPOUND_FINALS || finalPart.length == 1) {
            listOf(initial, finalPart, t)
        } else {
            null
        }
    }

    /**
     * A lexicon line: "<text> <phonemes...>".
     *
     * Returns null when any syllable cannot be expressed, so the caller can
     * fall back to the syllable library rather than write a line the model
     * would mispronounce.
     */
    fun line(text: String, keys: List<String>): String? {
        if (text.isEmpty() || keys.size != text.length) return null
        val out = StringBuilder(text)
        for ((i, c) in text.withIndex()) {
            when {
                PinyinRange.isHan(c) -> {
                    val ph = of(keys[i]) ?: return null
                    for (t in ph) if (t !in ALLOWED) return null
                    out.append(' ').append(ph.joinToString(" "))
                    out.append(' ').append(WORD)
                }
                c in PUNCTUATION -> {
                    val token = c.toString()
                    if (token !in ALLOWED) return null
                    out.append(' ').append(token)
                    out.append(' ').append(WORD)
                }
                // Anything else (latin, digits) has no representation; refuse.
                else -> return null
            }
        }
        return out.toString().trim()
    }

    /** A line for a single character, used to seed the per-utterance file. */
    fun charLine(char: Char, key: String): String? = line(char.toString(), listOf(key))
}

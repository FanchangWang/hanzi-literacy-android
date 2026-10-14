package com.pupil.hanzi.data

import android.content.Context
import java.io.IOException

/**
 * Decides which audio files exist.
 *
 * The dictionary lists more readings than the library has audio for -- "一"
 * alone yields yi1 yi2 yi4, and the assets do not cover all of them. Rather
 * than probe the filesystem on every play, we enumerate the voice directory
 * once on first use; it is only 2055 entries.
 */
class VoiceAssets(context: Context) {

    private val appContext = context.applicationContext

    /** Syllable keys with a matching mp3, e.g. "hao3". */
    val available: Set<String> by lazy { enumerate() }

    fun has(key: String): Boolean = key in available

    /**
     * The best asset for [key]: itself when present, otherwise the first of
     * [fallbacks] that exists, otherwise null so the caller can stay silent
     * rather than play something wrong.
     */
    fun resolve(key: String, fallbacks: List<String> = emptyList()): String? = when {
        has(key) -> key
        else -> fallbacks.firstOrNull { has(it) }
    }

    private fun enumerate(): Set<String> = try {
        appContext.assets.list("voice")
            ?.asSequence()
            ?.filter { it.endsWith(".mp3") }
            ?.map { it.removeSuffix(".mp3") }
            ?.toSet()
            .orEmpty()
    } catch (e: IOException) {
        emptySet()
    }
}
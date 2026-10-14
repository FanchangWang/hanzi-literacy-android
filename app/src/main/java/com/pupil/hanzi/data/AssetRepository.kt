package com.pupil.hanzi.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Reads the merged asset shards.
 *
 * Shards are 4096-codepoint buckets named `shard-s<hex>.json`, e.g. '好'
 * (U+597D) lands in shard-s5. Each shard is parsed at most once and then
 * held in memory; the largest is 6.4MB and parses in ~44ms, so lazy loading
 * per shard is cheaper than a database (measured -- see ANDROID_IMPLEMENTATION
 * §4.4).
 */
class AssetRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        // Shards are plain {"字": {...}} maps; no polymorphism to resolve.
        isLenient = true
    }

    private val strokeMutex = Mutex()
    private val explanationMutex = Mutex()

    private val strokeShards = HashMap<String, Map<String, StrokeData>>()
    private val explanationShards = HashMap<String, Map<String, Map<String, String>>>()

    /**
     * Shard file name for [char], e.g. "好" -> "shard-s5.json".
     *
     * Negative codepoints cannot occur for Char, but guard anyway so a bad
     * caller cannot produce a path that escapes the assets dir.
     */
    private fun shardName(char: Char): String {
        val bucket = char.code shr SHARD_BITS
        return "shard-s${Integer.toHexString(bucket)}.json"
    }

    /** Stroke data for [char], or null when the character has no strokes. */
    suspend fun strokesOf(char: Char): StrokeData? {
        if (!PinyinService.isHan(char)) return null
        val shard = loadShard(strokeMutex, strokeShards, "draw_all", char) ?: return null
        return shard[char.toString()]
    }

    /** True when [char] has stroke data, without parsing the whole shard
     *  beyond what is already cached. Used to decide grid eligibility. */
    suspend fun hasStrokes(char: Char): Boolean = strokesOf(char) != null

    /**
     * Definitions for words starting with [char].
     *
     * The explanation data is keyed by *word*, not by character, so a lookup
     * for '好' means every key in that character's file beginning with '好'.
     */
    suspend fun definitionsOf(char: Char): List<WordDefinition> {
        if (!PinyinService.isHan(char)) return emptyList()
        val shard = loadShard(explanationMutex, explanationShards, "explanation_all", char)
            ?: return emptyList()
        val entries = shard[char.toString()] ?: return emptyList()
        return entries.asSequence()
            .filter { it.key.length > 1 && it.key.startsWith(char) }
            // Short words first: 好人 before 好不容易.
            .sortedBy { it.key.length }
            .map { (word, definition) -> WordDefinition(word, definition) }
            .toList()
    }

    private suspend fun <T> loadShard(
        mutex: Mutex,
        cache: HashMap<String, T>,
        dir: String,
        char: Char,
    ): T? {
        val name = shardName(char)
        cache[name]?.let { return it }
        return mutex.withLock {
            // Re-check: another coroutine may have loaded it while we waited.
            cache[name]?.let { return it }
            val path = "$dir/$name"
            val text = try {
                withContext(Dispatchers.IO) {
                    context.assets.open(path).bufferedReader().use { it.readText() }
                }
            } catch (e: IOException) {
                // A missing shard just means this character has no data.
                Log.i(TAG, "no shard $path")
                return null
            }
            @Suppress("UNCHECKED_CAST")
            val parsed = when (dir) {
                "draw_all" -> json.decodeFromString<Map<String, StrokeData>>(text)
                else -> json.decodeFromString<Map<String, Map<String, String>>>(text)
            } as T
            cache[name] = parsed
            parsed
        }
    }

    private companion object {
        const val TAG = "AssetRepository"
        const val SHARD_BITS = 12
    }
}

/** A word and what it means, e.g. "好人" -> "1.对自己和对自己以外的人…". */
data class WordDefinition(
    val word: String,
    val definition: String,
)
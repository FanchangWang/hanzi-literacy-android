package com.pupil.hanzi.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import java.io.IOException

/**
 * Reads the merged asset shards.
 *
 * Shards are 4096-codepoint buckets named `shard-s<hex>.json`, e.g. '好'
 * (U+597D) lands in shard-s5. Each shard is parsed at most once and then held
 * in memory.
 *
 * The lock is **per shard**, not global. A single mutex serialised the three
 * shards that a four-character sentence touches, so 懂好重长 -- s6, s5 and s9
 * -- parsed one after another and took five seconds. Shards are independent, so
 * they load in parallel and only a repeated character in the same bucket
 * contends.
 */
class AssetRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val strokeLocks = HashMap<String, Mutex>()
    private val explanationLocks = HashMap<String, Mutex>()
    private val lockLock = Mutex()

    private val strokeShards = HashMap<String, Map<String, StrokeData>>()
    private val explanationShards = HashMap<String, Map<String, Map<String, String>>>()

    /** Stroke data for [char], or null when the character has no strokes. */
    suspend fun strokesOf(char: Char): StrokeData? {
        if (!PinyinService.isHan(char)) return null
        val shard = loadStrokeShard(char) ?: return null
        return shard[char.toString()]
    }

    /**
     * Definitions for words starting with [char].
     *
     * The explanation data is keyed by *word*, not by character, so a lookup
     * for '好' means every key in that character's file beginning with '好'.
     */
    suspend fun definitionsOf(char: Char): List<WordDefinition> {
        if (!PinyinService.isHan(char)) return emptyList()
        val shard = loadExplanationShard(char) ?: return emptyList()
        val entries = shard[char.toString()] ?: return emptyList()
        return entries.asSequence()
            .filter { it.key.length > 1 && it.key.startsWith(char) }
            // Short words first: 好人 before 好不容易.
            .sortedBy { it.key.length }
            .map { (word, definition) -> WordDefinition(word, definition) }
            .toList()
    }

    private suspend fun loadStrokeShard(char: Char): Map<String, StrokeData>? {
        val name = shardName(char)
        strokeShards[name]?.let { return it }
        val mutex = lockFor(strokeLocks, name)
        return mutex.withLock {
            strokeShards[name]?.let { return@withLock it }
            readShard("draw_all", name)?.let {
                decodeLenient<StrokeData>(it, "draw")
            }?.also { strokeShards[name] = it }
        }
    }

    private suspend fun loadExplanationShard(char: Char): Map<String, Map<String, String>>? {
        val name = shardName(char)
        explanationShards[name]?.let { return it }
        val mutex = lockFor(explanationLocks, name)
        return mutex.withLock {
            explanationShards[name]?.let { return@withLock it }
            readShard("explanation_all", name)?.let {
                decodeLenient<Map<String, String>>(it, "explanation")
            }?.also { explanationShards[name] = it }
        }
    }

    private suspend fun lockFor(map: HashMap<String, Mutex>, name: String): Mutex =
        lockLock.withLock { map.getOrPut(name) { Mutex() } }

    private suspend fun readShard(dir: String, name: String): String? = try {
        withContext(Dispatchers.IO) {
            context.assets.open("$dir/$name").bufferedReader().use { it.readText() }
        }
    } catch (e: IOException) {
        // A missing shard just means this character has no data.
        Log.i(TAG, "no shard $dir/$name")
        null
    }

    /**
     * Decodes a shard, tolerating individual characters that fail.
     *
     * A strict decode throws away the entire shard because of one bad entry,
     * and the data does contain irregular characters. Losing thousands of
     * characters to salvage one is the wrong trade, so undecodable entries are
     * dropped and the rest of the shard survives.
     */
    private inline fun <reified T> decodeLenient(text: String, what: String): Map<String, T> {
        val root = try {
            json.parseToJsonElement(text).jsonObject
        } catch (e: SerializationException) {
            Log.e(TAG, "$what shard is not valid JSON: ${e.message}")
            return emptyMap()
        }
        val out = LinkedHashMap<String, T>(root.size)
        var dropped = 0
        for ((key, element) in root) {
            val decoded: T? = try {
                json.decodeFromJsonElement<T>(element)
            } catch (e: SerializationException) {
                dropped++
                if (dropped <= MAX_LOGGED_DROPS) {
                    Log.w(TAG, "dropping undecodable $what '$key': ${e.message?.take(120)}")
                }
                null
            } catch (e: IllegalArgumentException) {
                dropped++
                null
            }
            if (decoded != null) out[key] = decoded
        }
        if (dropped > 0) Log.w(TAG, "$what: dropped $dropped of ${root.size} entries")
        return out
    }

    /** Shard file name for [char], e.g. "好" -> "shard-s5.json". */
    private fun shardName(char: Char): String {
        val bucket = char.code shr SHARD_BITS
        return "shard-s${Integer.toHexString(bucket)}.json"
    }

    private companion object {
        const val TAG = "AssetRepository"
        const val SHARD_BITS = 9
        const val MAX_LOGGED_DROPS = 5
    }
}

/** A word and what it means, e.g. "好人" -> "1.对自己和对自己以外的人…". */
data class WordDefinition(
    val word: String,
    val definition: String,
)
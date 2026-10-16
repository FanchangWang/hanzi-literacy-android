package com.pupil.hanzi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * How many confirmed texts are kept.
 *
 * 30 is weeks of daily use. The whole list is rewritten on every confirm, so
 * this number also bounds that write -- and a longer list only buys scrolling.
 * Past it the oldest entry goes.
 */
internal const val HISTORY_LIMIT = 30

/** One stored text and when it was last confirmed. */
@Serializable
data class HistoryEntry(
    val text: String,
    /** Wall-clock millis. 0 for an entry stored before times were kept. */
    val timeMillis: Long = 0L,
)

/**
 * [text] moved to the front of [history], which is ordered newest first.
 *
 * Confirming the same text twice is the same entry, not two: it moves to the
 * front and takes the newer [timeMillis] instead of being added again, so the
 * list stays a set of the texts the user actually studies. The list is capped
 * at [limit], oldest dropped.
 */
internal fun addToHistory(
    history: List<HistoryEntry>,
    text: String,
    timeMillis: Long,
    limit: Int = HISTORY_LIMIT,
): List<HistoryEntry> {
    if (text.isEmpty()) return history
    return (listOf(HistoryEntry(text, timeMillis)) + history.filter { it.text != text })
        .take(limit)
}

/** The stored form of the list. JSON, because a text can itself hold newlines. */
internal fun encodeHistory(history: List<HistoryEntry>): String = Json.encodeToString(history)

/**
 * The list as stored, or an empty one.
 *
 * Reads the older form too -- a bare array of strings, from before entries had
 * a time -- so an update does not wipe what is already on the device. A store
 * written by a newer version, or a truncated write, is worth an empty history
 * rather than a crash on launch.
 */
internal fun decodeHistory(raw: String?): List<HistoryEntry> {
    if (raw == null) return emptyList()
    return runCatching { Json.decodeFromString<List<HistoryEntry>>(raw) }
        .recoverCatching { Json.decodeFromString<List<String>>(raw).map { HistoryEntry(it) } }
        .getOrDefault(emptyList())
}

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "history",
)

/**
 * Persists the texts that were confirmed, newest first.
 *
 * A separate store from [SettingsStore]: this one grows with use and is a list,
 * the switches are four booleans that never change size.
 */
class HistoryStore(private val context: Context) {

    val history: Flow<List<HistoryEntry>> = context.historyDataStore.data
        .catch { e ->
            // A corrupt store must not take the app down; fall back to empty.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> decodeHistory(prefs[KEY_HISTORY]) }

    /** Move [text] to the front, adding it if it is new. */
    suspend fun record(text: String, timeMillis: Long = System.currentTimeMillis()) =
        context.historyDataStore.edit { prefs ->
            prefs[KEY_HISTORY] = encodeHistory(
                addToHistory(decodeHistory(prefs[KEY_HISTORY]), text, timeMillis),
            )
        }

    /** Drop the entry that reads exactly [text]. */
    suspend fun remove(text: String) = context.historyDataStore.edit { prefs ->
        prefs[KEY_HISTORY] = encodeHistory(
            decodeHistory(prefs[KEY_HISTORY]).filter { it.text != text },
        )
    }

    private companion object {
        val KEY_HISTORY = stringPreferencesKey("entries")
    }
}

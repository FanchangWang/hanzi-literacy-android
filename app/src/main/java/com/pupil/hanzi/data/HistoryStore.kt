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
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.IOException

/** How many confirmed texts are kept. */
internal const val HISTORY_LIMIT = 50

/**
 * [text] moved to the front of [history], which is ordered newest first.
 *
 * Confirming the same text twice is the same entry, not two: it moves to the
 * front instead of being added again, so the list stays a set of the texts the
 * user actually studies. The list is capped at [limit], oldest dropped.
 */
internal fun addToHistory(
    history: List<String>,
    text: String,
    limit: Int = HISTORY_LIMIT,
): List<String> {
    if (text.isEmpty()) return history
    return (listOf(text) + history.filter { it != text }).take(limit)
}

/** The stored form of the list. JSON, because a text can itself hold newlines. */
internal fun encodeHistory(history: List<String>): String = Json.encodeToString(history)

/**
 * The list as stored, or an empty one.
 *
 * A store written by an older version, or a truncated write, is worth an empty
 * history rather than a crash on launch.
 */
internal fun decodeHistory(raw: String?): List<String> =
    if (raw == null) {
        emptyList()
    } else {
        runCatching { Json.decodeFromString<List<String>>(raw) }.getOrDefault(emptyList())
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

    val history: Flow<List<String>> = context.historyDataStore.data
        .catch { e ->
            // A corrupt store must not take the app down; fall back to empty.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs -> decodeHistory(prefs[KEY_HISTORY]) }

    /** Move [text] to the front, adding it if it is new. */
    suspend fun record(text: String) = context.historyDataStore.edit { prefs ->
        prefs[KEY_HISTORY] = encodeHistory(addToHistory(decodeHistory(prefs[KEY_HISTORY]), text))
    }

    /** Drop the entry that reads exactly [text]. */
    suspend fun remove(text: String) = context.historyDataStore.edit { prefs ->
        prefs[KEY_HISTORY] = encodeHistory(
            decodeHistory(prefs[KEY_HISTORY]).filter { it != text },
        )
    }

    private companion object {
        val KEY_HISTORY = stringPreferencesKey("entries")
    }
}

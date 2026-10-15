package com.pupil.hanzi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The switches shown at the top of the screen. */
data class Settings(
    val showPinyin: Boolean = true,
    val animateStrokes: Boolean = true,
    val speakCharacters: Boolean = true,
    val keepScreenOn: Boolean = false,
) {
    companion object {
        val DEFAULT = Settings()
    }
}

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "settings",
)

/** Persists [Settings]. All four default accordingly. */
class SettingsStore(private val context: Context) {

    val settings: Flow<Settings> = context.settingsDataStore.data
        .catch { e ->
            // A corrupt store must not take the app down; fall back to defaults.
            if (e is IOException) emit(emptyPreferences()) else throw e
        }
        .map { prefs ->
            Settings(
                showPinyin = prefs[KEY_SHOW_PINYIN] ?: true,
                animateStrokes = prefs[KEY_ANIMATE] ?: true,
                speakCharacters = prefs[KEY_SPEAK] ?: true,
                keepScreenOn = prefs[KEY_KEEP_ON] ?: false,
            )
        }

    suspend fun setShowPinyin(value: Boolean) = edit { it[KEY_SHOW_PINYIN] = value }

    suspend fun setAnimateStrokes(value: Boolean) = edit { it[KEY_ANIMATE] = value }

    suspend fun setSpeakCharacters(value: Boolean) = edit { it[KEY_SPEAK] = value }

    suspend fun setKeepScreenOn(value: Boolean) = edit { it[KEY_KEEP_ON] = value }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit(block)
    }

    private companion object {
        val KEY_SHOW_PINYIN = booleanPreferencesKey("show_pinyin")
        val KEY_ANIMATE = booleanPreferencesKey("animation")
        val KEY_SPEAK = booleanPreferencesKey("voice")
        val KEY_KEEP_ON = booleanPreferencesKey("keep_screen_on")
    }
}
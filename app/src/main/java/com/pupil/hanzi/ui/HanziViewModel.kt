package com.pupil.hanzi.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pupil.hanzi.audio.AudioPlayer
import com.pupil.hanzi.data.AssetRepository
import com.pupil.hanzi.data.PinyinReading
import com.pupil.hanzi.data.PinyinService
import com.pupil.hanzi.data.Settings
import com.pupil.hanzi.data.SettingsStore
import com.pupil.hanzi.data.StrokeData
import com.pupil.hanzi.data.VoiceAssets
import com.pupil.hanzi.data.WordDefinition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One cell in the character grid. */
data class CharCell(
    val char: Char,
    val readings: List<PinyinReading>,
    /** Index into [readings]; a polyphone remembers the user's choice. */
    val selectedReading: Int = 0,
    val strokes: StrokeData? = null,
    val isPunctuation: Boolean = false,
) {
    val reading: PinyinReading? get() = readings.getOrNull(selectedReading)
    val isPolyphone: Boolean get() = readings.size > 1
    /** True when the dictionary offers a reading with no audio asset. */
    val readingHasAudio: Boolean get() = reading != null
}

/** What the detail sheet is showing. */
data class DetailState(
    val cell: CharCell,
    val definitions: List<WordDefinition> = emptyList(),
    val animate: Boolean = false,
    val progress: Float = 1f,
)

/** Editing vs viewing. */
enum class Mode { VIEWING, EDITING }

data class HanziUiState(
    val mode: Mode = Mode.EDITING,
    val draft: String = "",
    val lastConfirmed: String = "",
    val cells: List<CharCell> = emptyList(),
    val settings: Settings = Settings.DEFAULT,
    val detail: DetailState? = null,
    val message: String? = null,
) {
    val isEmpty: Boolean get() = cells.isEmpty()
}

class HanziViewModel(app: Application) : AndroidViewModel(app) {

    private val assets = AssetRepository(app)
    private val settingsStore = SettingsStore(app)
    private val voiceAssets = VoiceAssets(app)
    private val audio = AudioPlayer(app, voiceAssets)

    private val _state = MutableStateFlow(HanziUiState())
    val state: StateFlow<HanziUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                _state.update { it.copy(settings = s) }
            }
        }
    }

    fun onDraftChange(text: String) = _state.update { it.copy(draft = text) }

    fun startEditing() = _state.update { it.copy(mode = Mode.EDITING) }

    fun clearDraft() = _state.update { it.copy(draft = "") }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Confirm the draft and build the grid. */
    fun confirm() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(message = "请输入内容！") }
            return
        }
        _state.update { it.copy(mode = Mode.VIEWING, lastConfirmed = text) }
        viewModelScope.launch { build(text) }
    }

    private suspend fun build(text: String) {
        val cells = text.map { char ->
            val punctuation = !PinyinService.isHan(char)
            CharCell(
                char = char,
                readings = PinyinService.readingsOf(char),
                strokes = if (punctuation) null else assets.strokesOf(char),
                isPunctuation = punctuation,
            )
        }
        _state.update { it.copy(cells = cells) }
    }

    /** Switch a polyphone to [index]. */
    fun selectReading(charIndex: Int, index: Int) = _state.update { s ->
        s.copy(
            cells = s.cells.mapIndexed { i, cell ->
                if (i == charIndex && index in cell.readings.indices) {
                    cell.copy(selectedReading = index)
                } else {
                    cell
                }
            },
            detail = s.detail?.let { d ->
                if (d.cell.char == s.cells.getOrNull(charIndex)?.char) {
                    d.copy(cell = s.cells[charIndex])
                } else {
                    d
                }
            },
        )
    }

    /** Open the detail sheet for [charIndex]. */
    fun openDetail(charIndex: Int) {
        val cell = _state.value.cells.getOrNull(charIndex) ?: return
        viewModelScope.launch {
            val defs = if (cell.isPunctuation) emptyList() else assets.definitionsOf(cell.char)
            _state.update { it.copy(detail = DetailState(cell = cell, definitions = defs)) }
        }
    }

    fun closeDetail() = _state.update { it.copy(detail = null) }

    fun setShowPinyin(value: Boolean) = viewModelScope.launch {
        settingsStore.setShowPinyin(value)
    }

    fun setAnimate(value: Boolean) = viewModelScope.launch {
        settingsStore.setAnimateStrokes(value)
    }

    fun setSpeak(value: Boolean) = viewModelScope.launch {
        settingsStore.setSpeakCharacters(value)
    }

    /** Play one character with its currently selected reading. */
    fun speak(charIndex: Int) {
        val cell = _state.value.cells.getOrNull(charIndex) ?: return
        if (cell.isPunctuation) return
        val key = cell.reading?.key ?: return
        viewModelScope.launch {
            audio.play(key, cell.readings.map { it.key })
        }
    }

    /** Play the whole text, using word-level readings where available. */
    fun speakAll() {
        val s = _state.value
        val text = s.lastConfirmed
        if (text.isBlank()) return
        viewModelScope.launch {
            val phrase = PinyinService.phraseKeys(text)
            val perChar = text.filter { PinyinService.isHan(it) }
                .map { PinyinService.defaultKey(it) }
            // Prefer the phrase reading; fall back to per character if the
            // dictionary did not produce a matching count.
            val keys = if (phrase.size == perChar.size) phrase else perChar.filterNotNull()
            audio.playSequence(keys) { key -> keyListFor(key) }
        }
    }

    /** Other readings of the character owning [key], for audio fallback. */
    private fun keyListFor(key: String): List<String> {
        val cell = _state.value.cells.firstOrNull { it.reading?.key == key } ?: return emptyList()
        return cell.readings.map { it.key }
    }

    override fun onCleared() {
        super.onCleared()
        audio.release()
    }
}
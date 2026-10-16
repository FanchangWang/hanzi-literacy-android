package com.pupil.hanzi.ui

import android.app.Application
import android.content.ClipboardManager
import android.os.SystemClock
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pupil.hanzi.audio.AudioPlayer
import com.pupil.hanzi.data.AssetRepository
import com.pupil.hanzi.data.CharReading
import com.pupil.hanzi.data.HistoryStore
import com.pupil.hanzi.data.PinyinReading
import com.pupil.hanzi.data.PinyinService
import com.pupil.hanzi.data.Settings
import com.pupil.hanzi.data.SettingsStore
import com.pupil.hanzi.data.StrokeData
import com.pupil.hanzi.data.VoiceAssets
import com.pupil.hanzi.data.WordDefinition
import com.pupil.hanzi.nlp.Segmenters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One cell in the character grid. */
data class CharCell(
    val char: Char,
    /** Every reading, with the contextually correct one first. */
    val readings: List<PinyinReading>,
    /** Index into [readings]; a polyphone remembers the user's choice. */
    val selectedReading: Int = 0,
    val strokes: StrokeData? = null,
    val isPunctuation: Boolean = false,
) {
    val reading: PinyinReading? get() = readings.getOrNull(selectedReading)
    val isPolyphone: Boolean get() = readings.size > 1
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
    val cells: List<CharCell> = emptyList(),
    val settings: Settings = Settings.DEFAULT,
    val detail: DetailState? = null,
    val message: String? = null,
    /** Confirmed texts, newest first. */
    val history: List<String> = emptyList(),
    /** True while the first build is running, so the UI can say so. */
    val loading: Boolean = false,
    /** True once the stroke shards are cached, so a confirm need not wait. */
    val warmed: Boolean = false,
) {
    val isEmpty: Boolean get() = cells.isEmpty()
}

class HanziViewModel(app: Application) : AndroidViewModel(app) {

    private val assets = AssetRepository(app)
    private val settingsStore = SettingsStore(app)
    private val historyStore = HistoryStore(app)
    private val voiceAssets = VoiceAssets(app)
    private val audio = AudioPlayer(app, voiceAssets)

    private val _state = MutableStateFlow(HanziUiState())
    val state: StateFlow<HanziUiState> = _state.asStateFlow()

    init {
        // Warm the pinyin dictionary off the critical path. Its first call
        // initialises the segmentation data: 115ms on the JVM but 2.4s for four
        // characters on the device, which was the entire wait after tapping
        // confirm. Doing it here, in the background, means it is already hot
        // by the time the user types.
        // Warm both expensive paths off the critical path.
        //
        // Measured on device: the first confirm spent 2128ms parsing stroke
        // shards and 56ms on pinyin, while a second confirm took 31ms and
        // 129ms. So the shard cache, not the dictionary, is what made the
        // first tap slow -- it has to decompress the assets and build the
        // index once. Doing that here means it is warm before the user types.
        viewModelScope.launch(Dispatchers.Default) {
            val t = SystemClock.elapsedRealtime()
            Segmenters.ensure(getApplication())
            PinyinService.readings("好")
            Log.i(TAG, "warmup: pinyin ${SystemClock.elapsedRealtime() - t}ms crf=${Segmenters.isCrf}")
        }
        viewModelScope.launch(Dispatchers.IO) {
            val t = SystemClock.elapsedRealtime()
            assets.warmStrokes()
            Log.i(TAG, "warmup: shards ${SystemClock.elapsedRealtime() - t}ms")
            _state.update { it.copy(warmed = true) }
        }
        viewModelScope.launch {
            settingsStore.settings.collect { s ->
                _state.update { it.copy(settings = s) }
            }
        }
        viewModelScope.launch {
            historyStore.history.collect { entries ->
                _state.update { it.copy(history = entries) }
            }
        }
    }

    fun onDraftChange(text: String) = _state.update { it.copy(draft = text) }

    /**
     * Replace the draft with the clipboard text.
     *
     * Android 10+ only hands the clip to the focused app (or the default IME),
     * which is the case whenever a tap can arrive, so no permission or
     * request-focus dance is needed. [ClipData.Item.coerceToText] is used
     * rather than a cast to a plain-text item: it also covers a copied URI or
     * Intent, and an empty clipboard has to be reported rather than silently
     * wiping the draft.
     */
    fun pasteFromClipboard() {
        val clipboard = getApplication<Application>()
            .getSystemService(ClipboardManager::class.java)
        val clip = clipboard?.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).coerceToText(getApplication()).toString()
        } else {
            ""
        }
        if (text.isEmpty()) {
            _state.update { it.copy(message = "剪贴板没有文字") }
            return
        }
        _state.update { it.copy(draft = text) }
    }

    fun startEditing() = _state.update { it.copy(mode = Mode.EDITING) }

    fun clearDraft() = _state.update { it.copy(draft = "") }

    /**
     * Build the grid for a stored text, as if it had just been typed.
     *
     * The draft is replaced too, so returning to the input page shows the text
     * that was studied rather than whatever was left in the field.
     */
    fun studyHistory(text: String) {
        _state.update { it.copy(draft = text) }
        confirm()
    }

    /** Put a stored text back in the field, where it can be changed. */
    fun editHistory(text: String) = _state.update { it.copy(mode = Mode.EDITING, draft = text) }

    fun deleteHistory(text: String) = viewModelScope.launch {
        historyStore.remove(text)
    }

    fun dismissMessage() = _state.update { it.copy(message = null) }

    /** Confirm the draft and build the grid. */
    fun confirm() {
        val text = _state.value.draft.trim()
        if (text.isEmpty()) {
            _state.update { it.copy(message = "请输入内容！") }
            return
        }
        // Publish immediately: the grid frame appears at once with empty
        // character cells and the stroke data lands a moment later.
        _state.update {
            it.copy(mode = Mode.VIEWING, loading = true, cells = emptyList())
        }
        // Separate from the build: a disk write must not hold up the grid.
        viewModelScope.launch { historyStore.record(text) }
        viewModelScope.launch {
            try {
                build(text)
            } finally {
                _state.update { it.copy(loading = false) }
            }
        }
    }

    /**
     * Builds the grid in two stages so the screen appears immediately.
     *
     * Pinyin is pure computation and publishes first; the stroke drawings are
     * patched into the same cells as their shards arrive, so a slow shard never
     * holds up the whole grid.
     */
    private suspend fun build(text: String) {
        val t0 = SystemClock.elapsedRealtime()
        // Segment the whole string, then convert word by word. Converting one
        // character at a time loses the context, so 长江 would read zhǎng.
        //
        // The readings are consumed in order, never indexed by character: a
        // sentence can hold the same character twice with different readings
        // (行长 is xíng cháng, 长江 is cháng), and keying by character made
        // every position share one list, so the first reading won everywhere.
        val readings: List<CharReading> =
            withContext(Dispatchers.Default) { PinyinService.readings(text) }
        val t1 = SystemClock.elapsedRealtime()

        var cursor = 0
        val cells = text.map { char ->
            val punctuation = !PinyinService.isHan(char)
            val reading = if (punctuation) null else readings.getOrNull(cursor++)
            CharCell(
                char = char,
                // The contextually chosen reading first, then the alternates,
                // so index 0 is the one the phrase supports.
                readings = reading?.let { r ->
                    (listOf(r.selected) + r.alternates).distinctBy { it.key }
                }.orEmpty(),
                strokes = null,
                isPunctuation = punctuation,
            )
        }
        _state.update { it.copy(cells = cells) }
        val t2 = SystemClock.elapsedRealtime()
        Log.i(TAG, "build: pinyin=${t1 - t0}ms publish=${t2 - t1}ms")

        val wanted = text.mapIndexedNotNull { i, c ->
            if (PinyinService.isHan(c)) i to c else null
        }
        if (wanted.isEmpty()) return

        val loaded = coroutineScope {
            wanted.map { async(Dispatchers.IO) { assets.strokesOf(it.second) } }
                .awaitAll()
        }
        val t3 = SystemClock.elapsedRealtime()
        Log.i(TAG, "build: strokes=${t3 - t2}ms for ${wanted.size} chars")

        var patched = cells
        wanted.forEachIndexed { k, (index, _) ->
            val data = loaded.getOrNull(k)
            if (data != null && patched[index].strokes == null) {
                patched = patched.toMutableList().also {
                    it[index] = patched[index].copy(strokes = data)
                }
                _state.update { it.copy(cells = patched) }
            }
        }
        Log.i(TAG, "build: total=${SystemClock.elapsedRealtime() - t0}ms")
    }

    /**
     * Switch a polyphone to [index].
     */
    fun selectReading(charIndex: Int, index: Int) {
        _state.update { s ->
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

    fun setKeepScreenOn(value: Boolean) = viewModelScope.launch {
        settingsStore.setKeepScreenOn(value)
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

    /** Play a specific reading without changing the selection. */
    fun speakReading(charIndex: Int, readingIndex: Int) {
        val cell = _state.value.cells.getOrNull(charIndex) ?: return
        val reading = cell.readings.getOrNull(readingIndex) ?: return
        viewModelScope.launch {
            audio.play(reading.key, cell.readings.map { it.key })
        }
    }

    override fun onCleared() {
        super.onCleared()
        audio.release()
    }

    private companion object {
        const val TAG = "HanziViewModel"
    }
}
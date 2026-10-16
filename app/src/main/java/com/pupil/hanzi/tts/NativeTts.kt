package com.pupil.hanzi.tts

import android.content.Context
import android.util.Log
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.pupil.hanzi.nlp.Segmenters
import java.io.File

/**
 * The sherpa-onnx VITS engine, wrapped so the rest of the app never sees it.
 *
 * The model is phonemic and has no hanzi token, so every utterance needs a
 * lexicon. Rather than shipping the model's own 20,901-entry dictionary, we
 * write the file ourselves from the readings we already know; see
 * [Phonemes]. That is what keeps a polyphone reading exactly what the user
 * picked instead of whatever the model's own word list happens to say.
 *
 * The lexicon is a construction-time snapshot. character-lexicon.cc builds
 * word2ids_ and all_words_ inside Read() and never rereads the file, so the
 * order is fixed and cannot be rearranged:
 *
 *     write the lexicon  ->  build the engine  ->  generate
 *
 * A lexicon written after the engine exists is ignored and every character
 * comes out OOV, which produces 0.2s of fragments that sound like silence.
 * Measured with the file prepared first: a 22 character sentence yields
 * 117,640 samples, 5.3s of speech, for about 600ms of engine construction.
 *
 * The whole sentence goes into one generate() call. Splitting it word by word
 * measured the same 5.3s for 30ms more work, so there is nothing to gain.
 */
class NativeTts private constructor(
    private var tts: OfflineTts,
    private val lexiconFile: File,
    private val context: Context,
) {
    /** The lexicon the running engine was built against. */
    private var loadedLexicon: String? = null

    val sampleRate: Int get() = tts.sampleRate()

    /**
     * Synthesise [text] with the given reading per character.
     *
     * Returns null when any part cannot be spelled with the tokens the model
     * has, in which case the caller falls back to the syllable library.
     */
    fun synthesize(text: String, keys: List<String>): FloatArray? {
        if (keys.size != text.length) {
            Log.w(TAG, "${keys.size} keys for ${text.length} characters")
            return null
        }
        val lexicon = buildLexicon(text, keys) ?: run {
            Log.i(TAG, "cannot express '$text' with the model's tokens")
            return null
        }

        // Rebuild only when the lexicon actually changed. Pressing play on the
        // same sentence again costs nothing this way, and the ordering rule
        // from the class comment still holds for the first build and for every
        // rebuild after it.
        if (lexicon != loadedLexicon) {
            lexiconFile.writeText(lexicon)
            release()
            val fresh = create(context) ?: run {
                Log.w(TAG, "engine would not start")
                return null
            }
            tts = fresh.tts
            loadedLexicon = lexicon
        }

        return runCatching {
            tts.generate(text, sid = 0, speed = 1f).samples
        }.onFailure {
            Log.w(TAG, "synthesis failed for '$text'", it)
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    /**
     * Renders the lexicon: one line per HanLP word, each spelled with the
     * reading chosen for its characters.
     *
     * A single line for the whole sentence does not work -- sherpa segments the
     * text with its own longest-match rules, which do not agree with HanLP's,
     * so the key never matches and everything is OOV.
     */
    private fun buildLexicon(text: String, keys: List<String>): String? {
        val sb = StringBuilder()
        // Walk the text in step with the segmenter. Locating each word with
        // indexOf would read a repeated word from the wrong position and shift
        // every character after it.
        var cursor = 0
        for (term in Segmenters.segment(text)) {
            val word = term.word
            if (!text.startsWith(word, cursor)) {
                Log.w(TAG, "segment '$word' does not line up at $cursor")
                return null
            }
            val phonemes = phonemesFor(word, keys, cursor) ?: return null
            sb.append(word).append(' ').append(phonemes).append('\n')
            cursor += word.length
        }
        return sb.toString()
    }

    /** Phonemes for [word], which begins at [startAt] in the source text. */
    private fun phonemesFor(word: String, keys: List<String>, startAt: Int): String? {
        val sb = StringBuilder()
        for (i in word.indices) {
            val at = startAt + i
            if (at >= keys.size) return null
            if (PinyinRange.isHan(word[i])) {
                val key = keys[at]
                if (key.isEmpty()) return null
                val ph = Phonemes.of(key) ?: return null
                sb.append(ph.joinToString(" ")).append(' ').append(Phonemes.WORD).append(' ')
            } else {
                val token = word[i].toString()
                if (token !in Phonemes.ALLOWED) return null
                sb.append(token).append(' ').append(Phonemes.WORD).append(' ')
            }
        }
        return sb.toString().trim()
    }

    fun release() {
        runCatching { tts.release() }
        loadedLexicon = null
    }

    companion object {
        private const val TAG = "NativeTts"

        private const val ASSET_DIR = "tts"
        private const val MODEL = "zh_CN-xiao_ya-medium.onnx"
        private const val TOKENS = "tokens.txt"

        /**
         * Build the engine, copying the model and tokens out of the APK first
         * and addressing all three files by absolute path with a null
         * AssetManager.
         *
         * Neither half of the alternative works, and both fail silently in
         * different ways:
         *
         *  - passing an AssetManager together with absolute paths makes sherpa
         *    unable to choose a source, and it calls exit(255) from native code.
         *    That kills the process outright: no Java exception, nothing in
         *    logcat's crash buffer, and the app vanishes on launch. It
         *    documents the rule itself -- "Please set assetManager to null when
         *    you load model files from the SD card".
         *  - with assetManager null it only looks on disk, so an "tts/model.onnx"
         *    path is rejected as "does not exist".
         *
         * provider is a field of OfflineTtsModelConfig; OfflineTtsConfig
         * itself is (model, ruleFsts, ruleFars, numSentences, silenceScale).
         */
        fun create(context: Context): NativeTts? = runCatching {
            val app = context.applicationContext
            val dir = File(app.filesDir, "tts").apply { mkdirs() }
            val model = copyAsset(app, MODEL, File(dir, MODEL))
            val tokens = copyAsset(app, TOKENS, File(dir, TOKENS))
            val lexicon = File(dir, "lexicon.txt").apply { createNewFile() }

            val tts = OfflineTts(
                null,
                OfflineTtsConfig(
                    model = OfflineTtsModelConfig(
                        vits = OfflineTtsVitsModelConfig(
                            model = model.absolutePath,
                            lexicon = lexicon.absolutePath,
                            tokens = tokens.absolutePath,
                        ),
                        provider = "cpu",
                    ),
                    ruleFsts = "",
                    maxNumSentences = 1,
                ),
            )
            NativeTts(tts, lexicon, app)
        }.onFailure {
            Log.e(TAG, "could not create the TTS engine", it)
        }.getOrNull()

        private fun copyAsset(context: Context, name: String, into: File): File {
            if (into.exists() && into.length() > 0) return into
            context.assets.open("$ASSET_DIR/$name").use { input ->
                into.outputStream().use { output -> input.copyTo(output) }
            }
            return into
        }
    }
}

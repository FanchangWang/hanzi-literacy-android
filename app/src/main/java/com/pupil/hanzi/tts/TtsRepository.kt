package com.pupil.hanzi.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Speaks whole sentences with the sherpa model, falling back to the syllable
 * library when the model cannot spell a phrase.
 *
 * The engine is rebuilt for every utterance, because its lexicon is a
 * construction-time snapshot and the lexicon depends on what the user typed.
 * That costs about 600ms, which is the price of controlling the reading.
 */
class TtsRepository(private val context: Context) {

    @Volatile
    private var engine: NativeTts? = null

    @Volatile
    private var currentTrack: AudioTrack? = null

    /**
     * Speak [text] as one sentence, with [keys] giving the chosen reading of
     * each character and a blank for punctuation.
     *
     * Returns false when the model could not do it, which tells the caller to
     * use the syllable library instead.
     */
    suspend fun speak(text: String, keys: List<String>): Boolean {
        // Both steps touch disk and the 61MB model, so none of it may run on
        // the caller's dispatcher: viewModelScope is Main by default, and
        // building the engine there froze the UI and delayed the button.
        val ready = withContext(Dispatchers.IO) {
            engine ?: NativeTts.create(context).also { engine = it }
        } ?: return false
        val samples = withContext(Dispatchers.IO) {
            ready.synthesize(text, keys)
        } ?: run {
            Log.i(TAG, "model declined '$text'")
            return false
        }
        play(samples, ready.sampleRate)
        return true
    }

    /** Stop without tearing the engine down. */
    fun stop() {
        currentTrack?.let { track ->
            runCatching {
                if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
            }
        }
        currentTrack = null
    }

    /**
     * Streams the samples out, blocking until they finish.
     *
     * The model emits normalised floats in [-1, 1]. They are converted to
     * int16 because AudioTrack.write(float[]) into a MODE_STATIC buffer is
     * not reliably honoured across devices.
     */
    private suspend fun play(samples: FloatArray, sampleRate: Int) =
        withContext(Dispatchers.IO) {
            val pcm = ShortArray(samples.size) { i ->
                (samples[i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
            }

            val min = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
            )
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build(),
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(maxOf(min, pcm.size * 2))
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            try {
                track.write(pcm, 0, pcm.size)
                currentTrack = track
                track.play()
                Log.i(TAG, "playing ${pcm.size} samples @${sampleRate}Hz")

                // Wait on the frame counter, not on playState. A MODE_STATIC
                // track does not move itself out of PLAYING when the buffer
                // runs out, so a loop that waits for STOPPED never returns:
                // the audio finishes, the button stays on "stop", and
                // speak() never reports back so the UI never advances past
                // "synthesising".
                val deadline = SystemClock.elapsedRealtime() + MAX_PLAYBACK_MS
                while (track.playbackHeadPosition < pcm.size) {
                    if (SystemClock.elapsedRealtime() > deadline) {
                        Log.w(TAG, "playback did not finish within ${MAX_PLAYBACK_MS}ms")
                        break
                    }
                    Thread.sleep(5)
                }
            } catch (e: IllegalStateException) {
                Log.w(TAG, "playback error", e)
            } finally {
                runCatching { track.stop() }
                runCatching { track.release() }
                currentTrack = null
            }
        }

    fun release() {
        stop()
        engine?.release()
        engine = null
    }

    private companion object {
        const val TAG = "TtsRepository"

        /**
         * Upper bound on one utterance. A 40 character sentence is a few
         * seconds; anything past this is a track that never reported progress.
         */
        const val MAX_PLAYBACK_MS = 120_000L
    }
}

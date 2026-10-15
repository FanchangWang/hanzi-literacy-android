package com.pupil.hanzi.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Speaks whole sentences with the sherpa model, falling back to the syllable
 * library when the model cannot spell a phrase.
 *
 * Synthesis is the slow part: the engine is rebuilt around a lexicon that
 * depends on what the user typed, then the sentence is generated, about 600ms
 * plus the length of the audio. Both happen ahead of time in [prepare], once
 * the text is confirmed, so pressing play only plays a buffer.
 *
 * The cache key is the text together with every chosen reading. Switching a
 * polyphone changes the key and invalidates the audio, so it is rebuilt;
 * replaying the same sentence with the same readings hits the cache and costs
 * nothing.
 */
class TtsRepository(private val context: Context) {

    /** One prepared utterance. */
    private class Prepared(
        val samples: FloatArray,
        val sampleRate: Int,
    )

    @Volatile
    private var engine: NativeTts? = null

    @Volatile
    private var currentTrack: AudioTrack? = null

    private val prepared = HashMap<String, Prepared>()

    /**
     * Guards the cache and, more importantly, the engine.
     *
     * onnxruntime is not reentrant: two coroutines inside generate() at once
     * segfault inside libonnxruntime.so with no Java exception and nothing in
     * the crash buffer. Switching a polyphone starts a rebuild while a play is
     * still running, which is exactly that, so every path that touches the
     * engine is serialised here.
     */
    private val engineLock = Mutex()

    /** The utterance currently being built, so callers can wait on it. */
    private var inFlight: Pair<String, CompletableDeferred<Boolean>>? = null

    /**
     * Build the audio for [text] ahead of time.
     *
     * Safe to call repeatedly: one already built is skipped, and one already
     * being built is awaited rather than duplicated, because the engine can
     * only hold a single lexicon.
     */
    suspend fun prepare(text: String, keys: List<String>) {
        val key = cacheKey(text, keys) ?: return

        val existing = engineLock.withLock {
            if (prepared.containsKey(key)) {
                ALREADY_DONE
            } else {
                inFlight?.takeIf { it.first == key }?.second
            }
        }
        if (existing === ALREADY_DONE) return
        if (existing != null) {
            existing.await()
            return
        }

        val deferred = CompletableDeferred<Boolean>()
        engineLock.withLock { inFlight = key to deferred }

        val built = withContext(Dispatchers.IO) { build(text, keys) }
        engineLock.withLock {
            built?.let { prepared[key] = it }
            inFlight = null
        }
        deferred.complete(built != null)
    }

    /**
     * Speak [text] as one sentence, with [keys] giving the chosen reading of
     * each character and a blank for punctuation.
     *
     * Returns false when the model could not do it, which tells the caller to
     * use the syllable library instead.
     */
    suspend fun speak(text: String, keys: List<String>): Boolean {
        val key = cacheKey(text, keys)

        // Wait for any build that is running before touching the engine, so
        // this never runs alongside one.
        val waiting = engineLock.withLock {
            inFlight?.takeIf { key != null && it.first == key }?.second
        }
        if (waiting != null) {
            waiting.await()
            engineLock.withLock { prepared[key] }?.let { hit ->
                play(hit.samples, hit.sampleRate)
                return true
            }
        }

        key?.let { k ->
            engineLock.withLock { prepared[k] }?.let { hit ->
                play(hit.samples, hit.sampleRate)
                return true
            }
        }

        // Nothing built. If a build for a DIFFERENT utterance is running, it
        // will rebuild the engine underneath us, so wait for it to finish.
        engineLock.withLock { inFlight }?.let { (_, other) -> other.await() }

        val t0 = SystemClock.elapsedRealtime()
        val built = withContext(Dispatchers.IO) { build(text, keys) }
        if (built == null) {
            Log.i(TAG, "model declined '$text'")
            return false
        }
        key?.let { k -> engineLock.withLock { prepared[k] = built } }
        Log.i(TAG, "built on demand in ${SystemClock.elapsedRealtime() - t0}ms")
        play(built.samples, built.sampleRate)
        return true
    }

    /**
     * Builds [text], holding [engineLock] throughout: the engine is rebuilt
     * when the lexicon changes, and generate() must not overlap with anything
     * else touching it.
     */
    private suspend fun build(text: String, keys: List<String>): Prepared? =
        engineLock.withLock {
            val ready = engine ?: NativeTts.create(context).also { engine = it }
                ?: return@withLock null
            val samples = ready.synthesize(text, keys) ?: return@withLock null
            if (samples.isEmpty()) return@withLock null
            Prepared(samples, ready.sampleRate)
        }

    /**
     * Identifies an utterance by its text and the reading of every character.
     *
     * Switching a polyphone changes it, which is what makes the cache
     * invalidate; typing the same thing again does not.
     */
    private fun cacheKey(text: String, keys: List<String>): String? {
        if (keys.size != text.length) return null
        return text + " " + keys.joinToString(",")
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
     * Streams the samples out, blocking until they finishes.
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
                // runs out, so a loop waiting for STOPPED never returns and
                // the caller never learns the audio finished.
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

    /** Called from onCleared, where suspending is not possible. */
    fun release() {
        stop()
        engine?.release()
        engine = null
        synchronized(this) {
            prepared.clear()
            inFlight = null
        }
    }

    private companion object {
        const val TAG = "TtsRepository"

        /** Upper bound on one utterance. */
        const val MAX_PLAYBACK_MS = 120_000L

        /** Marker for "already prepared", distinct from an in-flight job. */
        private val ALREADY_DONE: CompletableDeferred<Boolean> =
            CompletableDeferred<Boolean>().also { it.complete(true) }
    }
}

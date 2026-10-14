package com.pupil.hanzi.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Log
import com.pupil.hanzi.data.VoiceAssets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Plays syllable mp3s.
 *
 * ExoPlayer is deliberately not used: these are 5-6KB single syllables, not
 * streaming media, so the platform MediaPlayer is enough and saves a couple of
 * MB of dependency plus its format negotiation and caching layers.
 *
 * The dictionary lists more readings than the library covers, so every play
 * resolves through [VoiceAssets] first. When nothing matches we stay silent
 * unless the caller opts into the platform fallback, because reading a
 * polyphone with the wrong tone is worse than saying nothing.
 */
class AudioPlayer(
    context: Context,
    private val voiceAssets: VoiceAssets,
) {
    private val appContext = context.applicationContext

    private var player: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    /**
     * Play one syllable and wait for it to finish.
     *
     * [fallbacks] are tried in order when [key] itself has no asset -- pass
     * the character's other readings so a rare missing syllable still makes
     * a sound rather than silence.
     *
     * Returns the key played, or null if nothing matched.
     */
    suspend fun play(key: String, fallbacks: List<String> = emptyList()): String? {
        val resolved = voiceAssets.resolve(key, fallbacks)
        if (resolved == null) {
            Log.i(TAG, "no audio for $key (${fallbacks.size} fallbacks tried)")
            return null
        }
        return playAsset(resolved)
    }

    /** Play a whole phrase as a sequence of syllables, in order. */
    suspend fun playSequence(keys: List<String>, fallbacksFor: (String) -> List<String>) {
        for (key in keys) {
            play(key, fallbacksFor(key))
        }
    }

    private suspend fun playAsset(key: String): String? = withContext(Dispatchers.IO) {
        val path = "voice/$key.mp3"
        var mp: MediaPlayer? = null
        try {
            appContext.assets.openFd(path).use { afd ->
                mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                val done = CompletableDeferred<Unit>()
                mp.setOnCompletionListener { done.complete(Unit) }
                mp.setOnErrorListener { _, _, _ ->
                    done.complete(Unit)
                    true
                }
                synchronized(this@AudioPlayer) { player = mp }
                mp.prepare()
                mp.start()
                done.await()
                key
            }
        } catch (e: IOException) {
            Log.w(TAG, "failed to play $path", e)
            null
        } catch (e: IllegalStateException) {
            Log.w(TAG, "player state error for $path", e)
            null
        } finally {
            mp?.release()
            synchronized(this@AudioPlayer) { if (player === mp) player = null }
        }
    }

    /**
     * Last-resort pronunciation via the platform engine, only reached when no
     * mp3 matches. The platform may misjudge a polyphone, which is exactly
     * what this project avoids everywhere else, so it is opt-in.
     */
    suspend fun playWithSystemTts(char: Char): Boolean {
        val engine = ensureTts() ?: return false
        val result = engine.speak(char.toString(), TextToSpeech.QUEUE_FLUSH, null, char.toString())
        return result == TextToSpeech.SUCCESS
    }

    fun stop() {
        synchronized(this) {
            player?.let { mp ->
                try {
                    if (mp.isPlaying) mp.stop()
                } catch (e: IllegalStateException) {
                    Log.w(TAG, "stop on an odd player state", e)
                }
            }
        }
    }

    private suspend fun ensureTts(): TextToSpeech? {
        tts?.let { return if (ttsReady) it else null }
        val ready = CompletableDeferred<Boolean>()
        val engine = TextToSpeech(appContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            ready.complete(ttsReady)
        }
        tts = engine
        return if (ready.await()) engine else null
    }

    fun release() {
        stop()
        synchronized(this) {
            player?.release()
            player = null
        }
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private companion object {
        const val TAG = "AudioPlayer"
    }
}
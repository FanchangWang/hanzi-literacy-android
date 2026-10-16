package com.pupil.hanzi.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.speech.tts.TextToSpeech
import android.util.Log
import com.pupil.hanzi.data.VoiceAssets
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Plays syllable mp3s.
 *
 * Each asset is a few kilobytes, so it is decoded once into memory and then
 * played back as PCM. Building a MediaPlayer per syllable meant re-opening the
 * asset, preparing and releasing for every character, and the silence between
 * those calls was long enough to be heard as a pause between words.
 *
 * ExoPlayer is not used: there is no streaming, no seeking and no format
 * negotiation here, only short PCM playback.
 */
class AudioPlayer(
    context: Context,
    private val voiceAssets: VoiceAssets,
) {
    private val appContext = context.applicationContext

    private val cache = HashMap<String, DecodedAudio>()
    private val cacheLock = Mutex()

    private var tts: TextToSpeech? = null
    @Volatile private var ttsReady = false

    /** Decoded samples plus the rate they were decoded at. */
    private data class DecodedAudio(val samples: ShortArray, val sampleRate: Int)

    /**
     * Play one syllable and wait for it to finish.
     *
     */
    suspend fun play(
        key: String,
        fallbacks: List<String> = emptyList(),
    ): String? {
        val resolved = voiceAssets.resolve(key, fallbacks)
        if (resolved == null) {
            Log.i(TAG, "no asset for $key (${fallbacks.size} fallbacks)")
            return null
        }
        val audio = decodedFor(resolved)
        if (audio == null) {
            Log.w(TAG, "decode failed for $resolved")
            return null
        }
        playPcm(resolved, audio)
        return resolved
    }

    /** Decodes the asset once and keeps the samples for later plays. */
    private suspend fun decodedFor(key: String): DecodedAudio? {
        cache[key]?.let { return it }
        return cacheLock.withLock {
            cache[key]?.let { return@withLock it }
            val decoded = withContext(Dispatchers.IO) { decode("voice/$key.mp3") }
            if (decoded != null) cache[key] = decoded
            decoded
        }
    }

    /**
     * Decodes an mp3 to 16-bit mono PCM.
     *
     * MediaPlayer has no PCM access, so this goes through MediaExtractor plus
     * MediaCodec: the extractor finds the track, the codec pumps it until it
     * reports end-of-stream, and the output buffers are concatenated.
     */
    private fun decode(path: String): DecodedAudio? {
        var extractor: MediaExtractor? = null
        var codec: MediaCodec? = null
        var afd: android.content.res.AssetFileDescriptor? = null
        return try {
            afd = appContext.assets.openFd(path)
            extractor = MediaExtractor().apply {
                setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            val track = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            }
            if (track == null) {
                Log.w(TAG, "no audio track in $path")
                return null
            }
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }

            val chunks = ArrayList<ShortArray>()
            val info = MediaCodec.BufferInfo()
            var sawOutput = false
            var sawInputEOS = false
            var rate = DEFAULT_RATE

            loop@ while (true) {
                if (!sawInputEOS) {
                    val inIndex = codec.dequeueInputBuffer(TIMEOUT_US)
                    if (inIndex >= 0) {
                        val buf = codec.getInputBuffer(inIndex)!!
                        val size = extractor.readSampleData(buf, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(
                                inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            sawInputEOS = true
                        } else {
                            codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outIndex = codec.dequeueOutputBuffer(info, TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        rate = codec.outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> {
                        if (sawInputEOS) break@loop
                    }

                    else -> {
                        if (outIndex >= 0) {
                            val out = codec.getOutputBuffer(outIndex)!!
                            if (info.size > 0) {
                                out.position(info.offset)
                                out.limit(info.offset + info.size)
                                val shortBuf = out.asShortBuffer()
                                val arr = ShortArray(shortBuf.remaining())
                                shortBuf.get(arr)
                                chunks += arr
                                sawOutput = true
                            }
                            codec.releaseOutputBuffer(outIndex, false)
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break@loop
                        }
                    }
                }
            }

            if (!sawOutput || chunks.isEmpty()) {
                Log.w(TAG, "decoded nothing from $path")
                null
            } else {
                val total = chunks.sumOf { it.size }
                val all = ShortArray(total)
                var at = 0
                chunks.forEach { c ->
                    c.copyInto(all, at)
                    at += c.size
                }
                DecodedAudio(all, rate)
            }
        } catch (e: Exception) {
            Log.w(TAG, "failed to decode $path", e)
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor?.release() }
            runCatching { afd?.close() }
        }
    }

    /**
     * Streams the samples out and blocks until they have finished.
     *
     * `AudioTrack.play()` is asynchronous: a MODE_STATIC track is already in
     * the PLAYING state before the first sample leaves, and only settles to
     * STOPPED once its buffer is spent. Waiting on `playState` alone returned
     * almost immediately and truncated the syllable. The written-frame count
     * is the reliable signal.
     */
    private suspend fun playPcm(key: String, audio: DecodedAudio) = withContext(Dispatchers.IO) {
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(audio.sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(audio.samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        try {
            t.write(audio.samples, 0, audio.samples.size)
            t.play()
            val expected = audio.samples.size.toLong()
            // Capped so a device that never reports progress cannot hang the
            // queue; 20s is far longer than any syllable.
            val deadline = System.currentTimeMillis() + PLAYBACK_TIMEOUT_MS
            while (t.playbackHeadPosition < expected && System.currentTimeMillis() < deadline) {
                Thread.sleep(5)
            }
            Log.i(TAG, "played $key: ${t.playbackHeadPosition}/$expected frames")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "track error during playback of $key", e)
        } finally {
            t.release()
        }
    }

    /**
     * Last-resort pronunciation via the platform engine, only reached when no
     * mp3 matches. The platform may misjudge a polyphone, which is exactly
     * what this project avoids everywhere else, so it is opt-in.
     */
    suspend fun playWithSystemTts(char: Char): Boolean {
        val engine = ensureTts() ?: return false
        return engine.speak(char.toString(), TextToSpeech.QUEUE_FLUSH, null, char.toString()) ==
            TextToSpeech.SUCCESS
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
        cache.clear()
        tts?.stop()
        tts?.shutdown()
        tts = null
        ttsReady = false
    }

    private companion object {
        const val TAG = "AudioPlayer"
        const val TIMEOUT_US = 10_000L
        const val DEFAULT_RATE = 22050
        const val PLAYBACK_TIMEOUT_MS = 20_000L
    }
}
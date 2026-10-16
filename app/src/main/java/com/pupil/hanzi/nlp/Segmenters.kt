package com.pupil.hanzi.nlp

import android.content.Context
import android.util.Log
import com.hankcs.hanlp.HanLP
import com.hankcs.hanlp.model.crf.CRFLexicalAnalyzer
import com.hankcs.hanlp.seg.Segment
import com.hankcs.hanlp.seg.common.Term
import java.io.File

/**
 * One shared segmenter for the whole app.
 *
 * The default [HanLP.segment] uses ViterbiSegment (dictionary shortest-path +
 * second-order HMM). This app instead loads the CRF CWS model bundled in
 * assets (hanlp_cws_crf.bin, ~12 MB) and hands
 * [com.pupil.hanzi.data.PinyinService] a [CRFLexicalAnalyzer]. CRF segmentation
 * is slower but more accurate on novel word boundaries, which matters for the
 * polyphone readings that depend on where a word starts and ends.
 *
 * [CRFLexicalAnalyzer] and not `com.hankcs.hanlp.seg.CRF.CRFSegment`: that
 * class is deprecated as a whole in 1.8.6, and its `String` constructor reads
 * a CRF++ *text* model. Our asset is a HanLP `.bin` model, so the old call
 * died in `CRFModel.loadTxt` with a NumberFormatException and [segment] fell
 * back to Viterbi for good. [CRFLexicalAnalyzer] takes the `.bin` name for
 * what it is (`LogLinearModel` loads it directly when the path ends in `.bin`)
 * and is the replacement the deprecation points at. Only the CWS model is
 * loaded, so [segment] yields words with a null part of speech -- callers must
 * not read `Term.nature`.
 *
 * Initialisation copies the model out of assets into filesDir on first call,
 * then builds the segmenter. It is lazy and idempotent; call [ensure] once at
 * startup on a background thread. Until then, [segment] falls back to the
 * built-in segmenter so the app never crashes on a cold start.
 */
object Segmenters {
    private const val TAG = "Segmenters"
    private const val ASSET_NAME = "hanlp_cws_crf.bin"
    private const val DIR_NAME = "nlp"

    @Volatile
    private var crf: Segment? = null

    /** Copy the model out of assets and build the CRF segmenter once. */
    fun ensure(context: Context) {
        if (crf != null) return
        synchronized(this) {
            if (crf != null) return
            try {
                val app = context.applicationContext
                val dir = File(app.filesDir, DIR_NAME).apply { mkdirs() }
                val model = File(dir, ASSET_NAME)
                if (!model.exists() || model.length() == 0L) {
                    app.assets.open(ASSET_NAME).use { input ->
                        model.outputStream().use { output -> input.copyTo(output) }
                    }
                }
                val t = System.nanoTime()
                crf = CRFLexicalAnalyzer(model.absolutePath)
                Log.i(TAG, "CRF segmenter ready in ${(System.nanoTime() - t) / 1_000_000}ms")
            } catch (e: Throwable) {
                Log.w(TAG, "CRF model unavailable, falling back to default segmenter", e)
                crf = null
            }
        }
    }

    /** Segment [text], preferring the CRF model and falling back if needed. */
    fun segment(text: String): List<Term> {
        val seg = crf
        return seg?.seg(text) ?: HanLP.segment(text)
    }

    /** True once the CRF model is loaded; false while still on the fallback. */
    val isCrf: Boolean get() = crf != null
}

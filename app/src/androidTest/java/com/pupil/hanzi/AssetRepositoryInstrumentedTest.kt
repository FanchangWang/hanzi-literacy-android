package com.pupil.hanzi

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pupil.hanzi.data.AssetRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs against the real merged assets on a device.
 *
 * The unit tests cannot catch this class of problem: the shard files only
 * exist inside the APK, and the crash that motivated this test came from one
 * character carrying a fractional median coordinate.
 */
@RunWith(AndroidJUnit4::class)
class AssetRepositoryInstrumentedTest {

    private val repo = AssetRepository(
        InstrumentationRegistry.getInstrumentation().targetContext,
    )

    @Test
    fun loadsEveryShardWithoutThrowing() = runBlocking {
        // One character per shard, including the sparse first and last buckets.
        val probes = listOf('一', '丁', '丂', '万', '丈', '好', '龘', '㐀', '仰')
        for (c in probes) {
            val data = repo.strokesOf(c)
            if (data != null) {
                assertTrue("$c has no strokes", data.strokes.isNotEmpty())
                assertEquals("$c strokes/medians length mismatch",
                    data.strokes.size, data.medians.size)
            }
        }
    }

    @Test
    fun charactersWithFractionalMediansDecode() = runBlocking {
        // These 32 characters carry non-integer median coordinates and used to
        // throw JsonDecodingException, taking their whole shard down with them.
        val fractional = listOf('懂', '巍', '撑', '曆', '榛', '歷', '溱', '瀉', '擊', '斃')
        for (c in fractional) {
            val data = repo.strokesOf(c)
            assertNotNull("$c should decode", data)
            requireNotNull(data).let {
                assertTrue("$c has no strokes", it.strokes.isNotEmpty())
            }
        }
    }

    @Test
    fun radicalIndicesAreWithinRange() = runBlocking {
        for (c in listOf('好', '江', '河', '他', '们', '重')) {
            val data = repo.strokesOf(c) ?: continue
            val radical = data.radicalStrokeIndices
            if (radical != null) {
                assertTrue("$c radical indices out of range: $radical vs ${data.strokes.size}",
                    radical.all { it in data.strokes.indices })
            }
        }
    }

    @Test
    fun definitionsAreWordKeyed() = runBlocking {
        val defs = repo.definitionsOf('好')
        assertTrue("expected definitions for 好", defs.isNotEmpty())
        assertTrue("keys must be words starting with 好",
            defs.all { it.word.length > 1 && it.word.startsWith('好') })
        // Short words first.
        val lengths = defs.map { it.word.length }
        assertEquals("definitions must be sorted short-first", lengths.sorted(), lengths)
    }

    @Test
    fun charactersWithoutDataReturnNullNotCrash() = runBlocking {
        assertNull(repo.strokesOf('龘'))
        assertNull(repo.strokesOf('，'))
        assertTrue(repo.definitionsOf('，').isEmpty())
    }
}
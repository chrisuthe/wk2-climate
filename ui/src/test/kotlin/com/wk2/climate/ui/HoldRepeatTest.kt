package com.wk2.climate.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HoldRepeatTest {

    @Test
    fun `a tap fires exactly once, immediately`() = runTest {
        var fired = 0
        val job = launch { HoldRepeat.run { fired++ } }
        // Released before the initial delay elapses.
        advanceTimeBy(50)
        job.cancel()
        assertEquals(1, fired)
    }

    @Test
    fun `nothing repeats before the initial delay`() = runTest {
        var fired = 0
        val job = launch { HoldRepeat.run { fired++ } }
        advanceTimeBy(HoldRepeat.INITIAL_DELAY_MS - 1)
        assertEquals("only the immediate fire so far", 1, fired)
        job.cancel()
    }

    @Test
    fun `the second fire lands at the initial delay`() = runTest {
        var fired = 0
        val job = launch { HoldRepeat.run { fired++ } }
        advanceTimeBy(HoldRepeat.INITIAL_DELAY_MS + 1)
        assertEquals(2, fired)
        job.cancel()
    }

    @Test
    fun `after the initial delay it repeats on the interval`() = runTest {
        var fired = 0
        val job = launch { HoldRepeat.run { fired++ } }
        // 400ms -> fire 2, then one per 150ms.
        advanceTimeBy(HoldRepeat.INITIAL_DELAY_MS + HoldRepeat.INTERVAL_MS * 4 + 1)
        assertEquals("1 immediate + 1 at 400ms + 4 intervals", 6, fired)
        job.cancel()
    }

    @Test
    fun `cancelling stops it`() = runTest {
        var fired = 0
        val job = launch { HoldRepeat.run { fired++ } }
        advanceTimeBy(HoldRepeat.INITIAL_DELAY_MS + HoldRepeat.INTERVAL_MS + 1)
        val atCancel = fired
        job.cancel()
        advanceTimeBy(10_000)
        assertEquals("no fires after cancellation", atCancel, fired)
    }

    @Test
    fun `the timings are the ones the design specifies`() = runEmpty {
        assertEquals(400L, HoldRepeat.INITIAL_DELAY_MS)
        assertEquals(150L, HoldRepeat.INTERVAL_MS)
    }

    /** A plain body, so the constants test needs no coroutine scope. */
    private fun runEmpty(body: () -> Unit) = body()
}

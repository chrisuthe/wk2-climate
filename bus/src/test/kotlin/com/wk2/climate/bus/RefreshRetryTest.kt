package com.wk2.climate.bus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RefreshRetryTest {

    private val delays = RefreshRetry.DEFAULT_DELAYS_MS

    /** Any non-empty set will do where only "still missing" matters. */
    private val someMissing = setOf(Signal.VOLUME, Signal.ILLUMINATION)

    @Test
    fun `the default schedule lands attempts at 1s 2s 4s and 8s`() {
        // The comment in RefreshRetry states the absolute schedule; the list is
        // per-attempt delays, so assert the two agree rather than trusting the
        // arithmetic in prose.
        val absolute = delays.runningReduce { acc, d -> acc + d }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), absolute)
    }

    @Test
    fun `nothing missing means no refresh and no delay`() = runTest {
        var refreshes = 0
        val settled = mutableListOf<Pair<Int, Set<Signal>>>()
        val job = launch {
            RefreshRetry.refreshUntilNothingMissing(
                missing = { emptySet() },
                refresh = { refreshes++ },
                onSettled = { attempts, missing -> settled += attempts to missing },
                delaysMs = delays,
            )
        }
        advanceUntilIdle()
        assertEquals("must not ask a bus that has already answered", 0, refreshes)
        assertEquals("must not sleep at all", 0L, currentTime)
        assertEquals("settles once, having asked nothing", listOf(0 to emptySet<Signal>()), settled)
        assertTrue("must have returned, not still be waiting", job.isCompleted)
    }

    @Test
    fun `signals never arriving fires every scheduled attempt and then stops for good`() = runTest {
        val attemptTimes = mutableListOf<Long>()
        val logged = mutableListOf<String>()
        var settled: Pair<Int, Set<Signal>>? = null
        val job = launch {
            RefreshRetry.refreshUntilNothingMissing(
                missing = { someMissing },
                refresh = { attemptTimes += currentTime },
                onAttempt = { attempt, total, missing -> logged += "$attempt/$total $missing" },
                onSettled = { attempts, missing -> settled = attempts to missing },
                delaysMs = delays,
            )
        }
        advanceUntilIdle()
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), attemptTimes)
        assertTrue("must be bounded, not retrying forever", job.isCompleted)

        // One log line per attempt, each naming what was still missing then,
        // plus exactly one settling line carrying the final set.
        assertEquals(
            listOf(
                "1/4 $someMissing",
                "2/4 $someMissing",
                "3/4 $someMissing",
                "4/4 $someMissing",
            ),
            logged,
        )
        assertEquals(4 to someMissing, settled)

        // A long way past the last attempt: nothing further may happen.
        advanceTimeBy(10 * 60_000L)
        advanceUntilIdle()
        assertEquals(4, attemptTimes.size)
    }

    @Test
    fun `the last missing signal arriving mid-schedule stops the remaining attempts`() = runTest {
        var missing = someMissing
        val attemptTimes = mutableListOf<Long>()
        var settled: Pair<Int, Set<Signal>>? = null
        val job = launch {
            RefreshRetry.refreshUntilNothingMissing(
                missing = { missing },
                refresh = { attemptTimes += currentTime },
                onSettled = { attempts, still -> settled = attempts to still },
                delaysMs = delays,
            )
        }
        // Let the first two attempts happen, then have the vehicle answer.
        advanceTimeBy(2_500L)
        assertEquals(listOf(1_000L, 2_000L), attemptTimes)
        missing = emptySet()
        advanceUntilIdle()

        assertEquals("no attempt after the last signal arrived", listOf(1_000L, 2_000L), attemptTimes)
        assertEquals(2 to emptySet<Signal>(), settled)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `a partial answer keeps going for the signals still missing`() = runTest {
        // The reported defect in miniature: climate is present from the first
        // moment, volume and illumination are not. Stopping on "some data
        // arrived" is what left them unasked for.
        var missing = someMissing
        val logged = mutableListOf<Set<Signal>>()
        launch {
            RefreshRetry.refreshUntilNothingMissing(
                missing = { missing },
                refresh = { if (missing.size == 2) missing = setOf(Signal.ILLUMINATION) },
                onAttempt = { _, _, still -> logged += still },
                delaysMs = delays,
            )
        }
        advanceUntilIdle()
        assertEquals(
            listOf(someMissing, setOf(Signal.ILLUMINATION), setOf(Signal.ILLUMINATION), setOf(Signal.ILLUMINATION)),
            logged,
        )
    }

    @Test
    fun `an answer to the first attempt means exactly one attempt`() = runTest {
        var missing = someMissing
        var refreshes = 0
        val job = launch {
            RefreshRetry.refreshUntilNothingMissing(
                missing = { missing },
                // Model the vendor service answering the re-registration.
                refresh = { refreshes++; missing = emptySet() },
                delaysMs = delays,
            )
        }
        advanceUntilIdle()
        assertEquals(1, refreshes)
        assertEquals("must not wait out the rest of the schedule", 1_000L, currentTime)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `cancelling the caller's scope stops it with no further attempts`() = runTest {
        var refreshes = 0
        var settled = 0
        val job = launch {
            RefreshRetry.refreshUntilNothingMissing(
                missing = { someMissing },
                refresh = { refreshes++ },
                onSettled = { _, _ -> settled++ },
                delaysMs = delays,
            )
        }
        advanceTimeBy(1_500L)
        assertEquals(1, refreshes)

        job.cancel()
        advanceUntilIdle()
        assertEquals("teardown must stop it mid-schedule", 1, refreshes)
        assertEquals("a cancelled retry does not log a settled line", 0, settled)
        assertTrue(job.isCancelled)
    }

    @Test
    fun `an empty schedule never refreshes but still settles`() = runTest {
        var refreshes = 0
        var settled: Pair<Int, Set<Signal>>? = null
        launch {
            RefreshRetry.refreshUntilNothingMissing(
                missing = { someMissing },
                refresh = { refreshes++ },
                onSettled = { attempts, missing -> settled = attempts to missing },
                delaysMs = emptyList(),
            )
        }
        advanceUntilIdle()
        assertEquals(0, refreshes)
        assertEquals(0L, currentTime)
        assertEquals(0 to someMissing, settled)
    }

    @Test
    fun `it drives a real bus and stops once every expected signal is present`() = runTest {
        // Against the fake, whose refresh() deliberately reports nothing: the
        // cold-start case where re-registration does not help.
        val silent = FakeVehicleBus(ClimateState.EMPTY)
        launch {
            RefreshRetry.refreshUntilNothingMissing(
                missing = { silent.state.value.missingSignals },
                refresh = { silent.refresh() },
                delaysMs = delays,
            )
        }
        advanceUntilIdle()
        assertEquals("every attempt used, none wasted", delays.size, silent.refreshes)

        // The measured baseline is missing only TEMP_OUT, so the retry must
        // keep going for it — this is the case the old `hasClimateData` rule
        // completed on immediately.
        val baseline = FakeVehicleBus(FakeVehicleBus.VEHICLE_BASELINE)
        launch {
            RefreshRetry.refreshUntilNothingMissing(
                missing = { baseline.state.value.missingSignals },
                refresh = {
                    baseline.refresh()
                    if (baseline.refreshes == 2) baseline.inject(Signal.TEMP_OUT, 0x10000744)
                },
                delaysMs = delays,
            )
        }
        advanceUntilIdle()
        assertEquals(2, baseline.refreshes)
    }
}

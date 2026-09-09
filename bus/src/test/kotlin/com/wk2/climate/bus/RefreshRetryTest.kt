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

    @Test
    fun `the default schedule lands attempts at 1s 2s 4s and 8s`() {
        // The comment in RefreshRetry states the absolute schedule; the list is
        // per-attempt delays, so assert the two agree rather than trusting the
        // arithmetic in prose.
        val absolute = delays.runningReduce { acc, d -> acc + d }
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), absolute)
    }

    @Test
    fun `data already present means no refresh and no delay`() = runTest {
        var refreshes = 0
        val job = launch {
            RefreshRetry.refreshUntilData(hasData = { true }, refresh = { refreshes++ }, delaysMs = delays)
        }
        advanceUntilIdle()
        assertEquals("must not ask a bus that has already answered", 0, refreshes)
        assertEquals("must not sleep at all", 0L, currentTime)
        assertTrue("must have returned, not still be waiting", job.isCompleted)
    }

    @Test
    fun `data never arrives fires every scheduled attempt and then stops for good`() = runTest {
        val attemptTimes = mutableListOf<Long>()
        val job = launch {
            RefreshRetry.refreshUntilData(
                hasData = { false },
                refresh = { attemptTimes += currentTime },
                delaysMs = delays,
            )
        }
        advanceUntilIdle()
        assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L), attemptTimes)
        assertTrue("must be bounded, not retrying forever", job.isCompleted)

        // A long way past the last attempt: nothing further may happen.
        advanceTimeBy(10 * 60_000L)
        advanceUntilIdle()
        assertEquals(4, attemptTimes.size)
    }

    @Test
    fun `data arriving mid-schedule stops it before the remaining attempts`() = runTest {
        var hasData = false
        val attemptTimes = mutableListOf<Long>()
        val job = launch {
            RefreshRetry.refreshUntilData(
                hasData = { hasData },
                refresh = { attemptTimes += currentTime },
                delaysMs = delays,
            )
        }
        // Let the first two attempts happen, then have the vehicle answer.
        advanceTimeBy(2_500L)
        assertEquals(listOf(1_000L, 2_000L), attemptTimes)
        hasData = true
        advanceUntilIdle()

        assertEquals("no attempt after the data arrived", listOf(1_000L, 2_000L), attemptTimes)
        assertTrue(job.isCompleted)
    }

    @Test
    fun `data arriving from the first attempt means exactly one attempt`() = runTest {
        var hasData = false
        var refreshes = 0
        val job = launch {
            RefreshRetry.refreshUntilData(
                hasData = { hasData },
                // Model the vendor service answering the re-registration.
                refresh = { refreshes++; hasData = true },
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
        val job = launch {
            RefreshRetry.refreshUntilData(
                hasData = { false },
                refresh = { refreshes++ },
                delaysMs = delays,
            )
        }
        advanceTimeBy(1_500L)
        assertEquals(1, refreshes)

        job.cancel()
        advanceUntilIdle()
        assertEquals("teardown must stop it mid-schedule", 1, refreshes)
    }

    @Test
    fun `an empty schedule never refreshes`() = runTest {
        var refreshes = 0
        launch {
            RefreshRetry.refreshUntilData(
                hasData = { false },
                refresh = { refreshes++ },
                delaysMs = emptyList(),
            )
        }
        advanceUntilIdle()
        assertEquals(0, refreshes)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `it drives a real bus and stops once the vehicle reports a climate signal`() = runTest {
        // Against the fake, whose refresh() deliberately reports nothing: the
        // cold-start case where re-registration does not help.
        val silent = FakeVehicleBus(ClimateState.EMPTY)
        launch {
            RefreshRetry.refreshUntilData(
                hasData = { silent.state.value.hasClimateData },
                refresh = { silent.refresh() },
                delaysMs = delays,
            )
        }
        advanceUntilIdle()
        assertEquals("every attempt used, none wasted", delays.size, silent.refreshes)

        // And the case where the vehicle does answer, on the second attempt.
        val answering = FakeVehicleBus(ClimateState.EMPTY)
        launch {
            RefreshRetry.refreshUntilData(
                hasData = { answering.state.value.hasClimateData },
                refresh = {
                    answering.refresh()
                    if (answering.refreshes == 2) answering.inject(Signal.AUTO, 0)
                },
                delaysMs = delays,
            )
        }
        advanceUntilIdle()
        assertEquals(2, answering.refreshes)
    }
}

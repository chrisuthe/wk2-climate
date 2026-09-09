package com.wk2.climate.bus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionGateTest {

    private val graceMs = 4_000L

    @Test
    fun `already connected shows immediately with no grace delay`() = runTest {
        val connected = MutableStateFlow(true)
        val shows = mutableListOf<Long>()
        val hides = mutableListOf<Long>()
        val job = launch {
            ConnectionGate.followConnection(connected, graceMs, { shows += currentTime }, { hides += currentTime })
        }
        advanceUntilIdle()
        assertEquals("no time should have elapsed", 0L, currentTime)
        assertTrue("show must have fired", shows.isNotEmpty())
        assertTrue("hide must never fire when already connected", hides.isEmpty())
        job.cancel()
    }

    @Test
    fun `never connects shows first then hides after exactly the grace period`() = runTest {
        val connected = MutableStateFlow(false)
        val shows = mutableListOf<Long>()
        val hides = mutableListOf<Long>()
        val job = launch {
            ConnectionGate.followConnection(connected, graceMs, { shows += currentTime }, { hides += currentTime })
        }
        advanceUntilIdle()
        assertEquals(listOf(0L), shows)
        assertEquals(listOf(graceMs), hides)
        job.cancel()
    }

    @Test
    fun `connecting during the grace window keeps it shown and hide is never called`() = runTest {
        val connected = MutableStateFlow(false)
        val shows = mutableListOf<Long>()
        val hides = mutableListOf<Long>()
        val job = launch {
            ConnectionGate.followConnection(connected, graceMs, { shows += currentTime }, { hides += currentTime })
        }
        // Advance to the middle of the grace window, then connect before it expires.
        advanceTimeBy(graceMs / 2)
        connected.value = true
        advanceUntilIdle()
        assertTrue("show must have fired at least once", shows.isNotEmpty())
        assertTrue("hide must never fire — the bus connected inside the grace window", hides.isEmpty())
        job.cancel()
    }

    @Test
    fun `disconnecting later hides immediately with no second grace delay`() = runTest {
        val connected = MutableStateFlow(true)
        val shows = mutableListOf<Long>()
        val hides = mutableListOf<Long>()
        val job = launch {
            ConnectionGate.followConnection(connected, graceMs, { shows += currentTime }, { hides += currentTime })
        }
        advanceUntilIdle()
        // Well past the grace window and long after startup, so a delayed hide
        // would be visibly distinguishable from an immediate one.
        advanceTimeBy(graceMs * 3)
        connected.value = false
        advanceUntilIdle()
        assertEquals("hide must fire at the moment of disconnect, not graceMs later", listOf(graceMs * 3), hides)
        job.cancel()
    }

    @Test
    fun `reconnecting after a disconnect shows again`() = runTest {
        val connected = MutableStateFlow(true)
        val shows = mutableListOf<Long>()
        val hides = mutableListOf<Long>()
        val job = launch {
            ConnectionGate.followConnection(connected, graceMs, { shows += currentTime }, { hides += currentTime })
        }
        advanceUntilIdle()
        val showsBeforeCycle = shows.size

        connected.value = false
        advanceUntilIdle()
        assertEquals(1, hides.size)

        connected.value = true
        advanceUntilIdle()
        assertTrue("show must fire again on reconnect", shows.size > showsBeforeCycle)
        assertEquals("no further hide from the reconnect itself", 1, hides.size)
        job.cancel()
    }

    @Test
    fun `cancelling the caller's scope stops it with no further callbacks`() = runTest {
        val connected = MutableStateFlow(false)
        val shows = mutableListOf<Long>()
        val hides = mutableListOf<Long>()
        val job = launch {
            ConnectionGate.followConnection(connected, graceMs, { shows += currentTime }, { hides += currentTime })
        }
        advanceUntilIdle() // grace expires, hidden once
        val showsAtCancel = shows.size
        val hidesAtCancel = hides.size

        job.cancel()
        connected.value = true
        advanceUntilIdle()

        assertEquals("no further show after cancellation", showsAtCancel, shows.size)
        assertEquals("no further hide after cancellation", hidesAtCancel, hides.size)
    }
}

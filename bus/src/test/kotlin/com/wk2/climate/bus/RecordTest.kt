package com.wk2.climate.bus

import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Test

class RecordTest {

    /**
     * Each vendor module calls back on its own binder thread, so updates land
     * concurrently. On the vehicle a module 4 update erased the canbus id that
     * module 7 had just written, and the id is sent once per registration: the
     * app sat at Waiting for good. No update may cost another its write.
     */
    @Test
    fun `concurrent updates from several threads are all kept`() {
        val signals = Signal.entries
        repeat(500) { round ->
            val flow = MutableStateFlow(ClimateState.EMPTY)
            val go = CountDownLatch(1)
            val threads = signals.map { s ->
                Thread { go.await(); flow.record(s, 1) }.also { it.start() }
            }
            go.countDown()
            threads.forEach { it.join() }
            val lost = signals.filterNot { flow.value.has(it) }
            assertEquals("round $round lost $lost", emptyList<Signal>(), lost)
        }
    }
}

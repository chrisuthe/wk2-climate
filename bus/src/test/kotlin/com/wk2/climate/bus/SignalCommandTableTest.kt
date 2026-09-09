package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalCommandTableTest {

    @Test
    fun `signal module and code pairs are unique`() {
        val pairs = Signal.entries.map { it.module to it.code }
        assertEquals(pairs.size, pairs.toSet().size)
    }

    @Test
    fun `signal lookup round trips`() {
        Signal.entries.forEach { s ->
            assertEquals(s, Signal.of(s.module, s.code))
        }
    }

    @Test
    fun `signal lookup returns null for an unknown pair`() {
        assertNull(Signal.of(7, 9999))
    }

    @Test
    fun `signals span exactly the three modules we bind`() {
        assertEquals(setOf(0, 4, 7), Signal.modules())
    }

    @Test
    fun `MODULE_CANBUS is the module every climate signal uses`() {
        // Every module-7 signal is a climate signal, and every climate signal
        // is on module 7 — the two sets must coincide with the named constant
        // SyuVehicleBus gates `connected` on, not a bare 7 at the call site.
        val climateSignals = Signal.entries.filter { it.module == Signal.MODULE_CANBUS }
        assertEquals(Signal.entries.filter { it.module == 7 }, climateSignals)
        assertTrue("expected climate signals on MODULE_CANBUS", climateSignals.isNotEmpty())
        assertTrue(Signal.AUTO in climateSignals)
    }

    @Test
    fun `we never subscribe to the high rate flood codes`() {
        // U_SPECTRUM is module 4 code 0; U_CANBUS_FRAME_TO_UI is module 7 code 1019.
        assertNull(Signal.of(4, 0))
        assertNull(Signal.of(7, 1019))
    }

    @Test
    fun `climate commands all use the fixed code 6 and a leading 1`() {
        Command.entries.filter { it.module == 7 }.forEach { c ->
            assertEquals("${c.name} must use command code 6", 6, c.code)
            assertEquals("${c.name} payload must be {1, index}", 2, c.payload.size)
            assertEquals("${c.name} payload[0] must be 1", 1, c.payload[0])
        }
    }

    @Test
    fun `climate command indices are unique and inside the known space`() {
        val indices = Command.entries.filter { it.module == 7 }.map { it.payload[1] }
        assertEquals(indices.size, indices.toSet().size)
        indices.forEach { assertTrue("index $it out of range 1..25", it in 1..25) }
    }

    @Test
    fun `unmapped and duplicate vehicle indices are omitted`() {
        val indices = Command.entries.filter { it.module == 7 }.map { it.payload[1] }.toSet()
        // 19 has no effect on this vehicle; 25 duplicates 10 (airflow -> feet).
        assertTrue("index 19 is unmapped and must not be exposed", 19 !in indices)
        assertTrue("index 25 duplicates 10 and must not be exposed", 25 !in indices)
    }

    @Test
    fun `climate power is the only destructive command`() {
        val destructive = Command.entries.filter { it.destructive }
        assertEquals(listOf(Command.CLIMATE_POWER), destructive)
        assertEquals(16, Command.CLIMATE_POWER.payload[1])
    }

    @Test
    fun `volume commands use the sound module convention`() {
        listOf(Command.VOL_UP to -1, Command.VOL_DOWN to -2, Command.VOL_HIDE_OSD to -7)
            .forEach { (cmd, sentinel) ->
                assertEquals("${cmd.name} is on the SOUND module", 4, cmd.module)
                assertEquals("${cmd.name} uses C_VOL code 0", 0, cmd.code)
                assertEquals("${cmd.name} payload is a single sentinel", 1, cmd.payload.size)
                assertEquals(sentinel, cmd.payload[0])
            }
    }
}

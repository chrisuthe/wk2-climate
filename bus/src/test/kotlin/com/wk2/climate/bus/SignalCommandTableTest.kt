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
    fun `climate commands use code 6, rear-zone commands code 8, and none carries an index of its own`() {
        // The index is per vehicle and comes from CommandTable; see
        // VehicleCatalogTest for the WK2's hand-swept values and the rear
        // page's numbers.
        val rear = setOf(
            Command.REAR_POWER, Command.REAR_AUTO, Command.REAR_TEMP_UP, Command.REAR_TEMP_DOWN,
            Command.REAR_FAN_UP, Command.REAR_FAN_DOWN, Command.REAR_FACE, Command.REAR_FACE_FEET,
            Command.REAR_FEET, Command.REAR_LOCK,
        )
        Command.entries.filter { it.module == 7 }.forEach { c ->
            assertEquals("${c.name} command code", if (c in rear) 8 else 6, c.code)
            assertEquals("${c.name} must not bake in a payload", 0, c.payload.size)
        }
    }

    @Test
    fun `climate power is the only destructive command`() {
        val destructive = Command.entries.filter { it.destructive }
        assertEquals(listOf(Command.CLIMATE_POWER), destructive)
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

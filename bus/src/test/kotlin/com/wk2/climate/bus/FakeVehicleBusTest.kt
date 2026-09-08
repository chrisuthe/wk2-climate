package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeVehicleBusTest {

    private fun bus() = FakeVehicleBus()
    private val FakeVehicleBus.now get() = state.value

    @Test
    fun `it starts at the measured vehicle baseline`() {
        val s = bus().now
        assertTrue(s.powerOn)
        assertTrue(s.autoOn)
        assertEquals(Fan.Auto, s.fan)
        assertEquals(Temp.Degrees(68), s.tempLeft)
        assertEquals(AirflowMode.NONE, s.airflow)
    }

    // ---- seat heat: the measured 3-state cycle ----

    @Test
    fun `seat heat cycles off high low off, never visiting two`() {
        val b = bus()
        assertEquals(SeatLevel.OFF, b.now.seatHeatL)

        b.send(Command.SEAT_HEAT_L)
        assertEquals(SeatLevel.HIGH, b.now.seatHeatL)
        assertEquals("raw must be 3", 3, b.now[Signal.SEAT_HEAT_L])

        b.send(Command.SEAT_HEAT_L)
        assertEquals(SeatLevel.LOW, b.now.seatHeatL)
        assertEquals("raw must be 1, skipping 2", 1, b.now[Signal.SEAT_HEAT_L])

        b.send(Command.SEAT_HEAT_L)
        assertEquals(SeatLevel.OFF, b.now.seatHeatL)
    }

    @Test
    fun `every seat heat tap changes the presented state`() {
        // This is the regression guard for the double-send workaround that was
        // specified and then removed. If a tap ever produces no visible change,
        // this fails.
        val b = bus()
        var previous = b.now.seatHeatL
        repeat(6) {
            b.send(Command.SEAT_HEAT_L)
            assertNotEquals("a tap must always change the presented state", previous, b.now.seatHeatL)
            previous = b.now.seatHeatL
        }
    }

    @Test
    fun `turning seat heat on zeroes seat vent, as the protocol does`() {
        val b = bus()
        b.inject(Signal.SEAT_VENT_L, 3)
        assertEquals(SeatLevel.HIGH, b.now.seatVentL)

        b.send(Command.SEAT_HEAT_L)
        assertEquals(SeatLevel.HIGH, b.now.seatHeatL)
        assertEquals("mutual exclusion is enforced in the protocol", SeatLevel.OFF, b.now.seatVentL)
    }

    // ---- fan ----

    @Test
    fun `the first fan up exits AUTO and lands on three`() {
        val b = bus()
        b.send(Command.FAN_UP)
        assertFalse("AUTO must clear", b.now.autoOn)
        assertEquals(Fan.Level(3), b.now.fan)
        assertEquals("leaving AUTO materialises FACE", AirflowMode.FACE, b.now.airflow)
    }

    @Test
    fun `fan climbs to seven then clamps`() {
        val b = bus()
        repeat(10) { b.send(Command.FAN_UP) }
        assertEquals(Fan.Level(7), b.now.fan)
    }

    @Test
    fun `fan down stops at zero`() {
        val b = bus()
        repeat(12) { b.send(Command.FAN_DOWN) }
        assertEquals(Fan.Level(0), b.now.fan)
    }

    @Test
    fun `AUTO restores the baseline in one command`() {
        val b = bus()
        repeat(4) { b.send(Command.FAN_UP) }
        b.send(Command.AUTO)
        assertTrue(b.now.autoOn)
        assertTrue("the AUTO macro also forces A C on", b.now.acOn)
        assertEquals(Fan.Auto, b.now.fan)
        assertEquals("the AUTO macro clears body blow", AirflowMode.NONE, b.now.airflow)
    }

    // ---- airflow ----

    @Test
    fun `airflow setters are idempotent`() {
        val b = bus()
        b.send(Command.AIRFLOW_FEET)
        val once = b.now
        b.send(Command.AIRFLOW_FEET)
        assertEquals("re-tapping the active mode must be a no-op", once, b.now)
        assertEquals(AirflowMode.FEET, b.now.airflow)
    }

    @Test
    fun `each airflow setter selects exactly its own mode`() {
        AirflowMode.selectable.forEach { mode ->
            val b = bus()
            b.send(mode.command!!)
            assertEquals(mode, b.now.airflow)
        }
    }

    // ---- macros ----

    @Test
    fun `MAX AC drives temperatures to the LO sentinel and forces recirc`() {
        val b = bus()
        b.send(Command.MAX_AC)
        assertEquals(Temp.Lo, b.now.tempLeft)
        assertEquals(Temp.Lo, b.now.tempRight)
        assertTrue(b.now.recircOn)
        assertTrue(b.now.maxAcOn)
    }

    @Test
    fun `front defrost forces recirc on and leaves airflow unrecognised`() {
        val b = bus()
        b.send(Command.FRONT_DEFROST)
        assertTrue(b.now.frontDefrostOn)
        assertTrue(b.now.recircOn)
        assertEquals("this is what UNKNOWN exists for", AirflowMode.UNKNOWN, b.now.airflow)
    }

    // ---- temperature and volume ----

    @Test
    fun `temperature steps one degree per command per zone`() {
        val b = bus()
        b.send(Command.TEMP_L_UP)
        assertEquals(Temp.Degrees(69), b.now.tempLeft)
        assertEquals("the other zone must not move", Temp.Degrees(68), b.now.tempRight)
        b.send(Command.TEMP_L_DOWN)
        assertEquals(Temp.Degrees(68), b.now.tempLeft)
    }

    @Test
    fun `volume steps and never goes negative`() {
        val b = bus()
        b.send(Command.VOL_UP)
        assertEquals(11, b.now.volume)
        repeat(30) { b.send(Command.VOL_DOWN) }
        assertEquals(0, b.now.volume)
    }

    @Test
    fun `hiding the volume OSD changes no state`() {
        val b = bus()
        val before = b.now
        b.send(Command.VOL_HIDE_OSD)
        assertEquals(before, b.now)
    }

    // ---- observability ----

    @Test
    fun `it records what was sent, so UI tests can assert dispatch`() {
        val b = bus()
        b.send(Command.AC)
        b.send(Command.SYNC)
        assertEquals(listOf(Command.AC, Command.SYNC), b.sent)
    }

    @Test
    fun `connection state is observable and settable`() {
        val b = bus()
        assertTrue(b.connected.value)
        b.setConnected(false)
        assertFalse(b.connected.value)
    }

    @Test
    fun `commands are ignored while disconnected`() {
        val b = bus()
        b.setConnected(false)
        val before = b.now
        b.send(Command.AC)
        assertEquals(before, b.now)
    }
}

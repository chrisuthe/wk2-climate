package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ClimateStateTest {

    @Test
    fun `an empty state reports every derived value as unavailable`() {
        val s = ClimateState.EMPTY
        assertTrue(s.isEmpty)
        assertEquals(Temp.Unavailable, s.tempLeft)
        assertEquals(Temp.Unavailable, s.tempRight)
        assertEquals(Fan.Unavailable, s.fan)
        assertEquals(AirflowMode.UNKNOWN, s.airflow)
        assertEquals(SeatLevel.UNAVAILABLE, s.seatHeatL)
        assertNull(s.volume)
    }

    @Test
    fun `an empty state reports flags as false rather than throwing`() {
        val s = ClimateState.EMPTY
        assertFalse(s.acOn)
        assertFalse(s.autoOn)
        assertFalse(s.powerOn)
        assertFalse(s.isNight)
    }

    @Test
    fun `with stores a raw value and derives from it`() {
        val s = ClimateState.EMPTY.with(Signal.TEMP_LEFT, 68)
        assertEquals(68, s[Signal.TEMP_LEFT])
        assertEquals(Temp.Degrees(68), s.tempLeft)
        assertFalse(s.isEmpty)
    }

    @Test
    fun `with returns the same instance when the value is unchanged`() {
        // The bus is push-on-change, but a re-registration replays current
        // values. Returning the identical instance keeps StateFlow from
        // re-emitting and re-composing the whole bar for nothing.
        val s = ClimateState.EMPTY.with(Signal.TEMP_LEFT, 68)
        assertSame(s, s.with(Signal.TEMP_LEFT, 68))
    }

    @Test
    fun `with returns a new instance when the value changes`() {
        val s = ClimateState.EMPTY.with(Signal.TEMP_LEFT, 68)
        val t = s.with(Signal.TEMP_LEFT, 69)
        assertEquals(Temp.Degrees(69), t.tempLeft)
        assertEquals("original must be untouched", Temp.Degrees(68), s.tempLeft)
    }

    @Test
    fun `the measured vehicle baseline derives correctly`() {
        // Exactly the snapshot captured from the vehicle on 2026-09-08.
        val s = ClimateState.EMPTY
            .with(Signal.POWER, 1)
            .with(Signal.AC, 1)
            .with(Signal.AUTO, 1)
            .with(Signal.SYNC, 1)
            .with(Signal.RECIRC, 0)
            .with(Signal.WIND_LEVEL, 15)
            .with(Signal.TEMP_LEFT, 68)
            .with(Signal.TEMP_RIGHT, 68)
            .with(Signal.BLOW_UP, 0)
            .with(Signal.BLOW_BODY, 0)
            .with(Signal.BLOW_FOOT, 0)
            .with(Signal.SEAT_HEAT_L, 0)
            .with(Signal.SEAT_HEAT_R, 0)
            .with(Signal.WHEEL_HEAT, 0)
            .with(Signal.VOLUME, 10)
            .with(Signal.ILLUMINATION, 0)

        assertTrue(s.powerOn)
        assertTrue(s.acOn)
        assertTrue(s.autoOn)
        assertTrue(s.syncOn)
        assertFalse(s.recircOn)
        assertEquals(Fan.Auto, s.fan)
        assertEquals(Temp.Degrees(68), s.tempLeft)
        assertEquals(Temp.Degrees(68), s.tempRight)
        assertEquals(AirflowMode.NONE, s.airflow)
        assertEquals(SeatLevel.OFF, s.seatHeatL)
        assertFalse(s.wheelHeatOn)
        assertEquals(10, s.volume)
        assertFalse("illumination 0 is day", s.isNight)
    }

    @Test
    fun `illumination one is night`() {
        assertTrue(ClimateState.EMPTY.with(Signal.ILLUMINATION, 1).isNight)
    }

    @Test
    fun `the MAX AC macro outcome renders as LO, not as a number`() {
        val s = ClimateState.EMPTY
            .with(Signal.TEMP_LEFT, -2)
            .with(Signal.TEMP_RIGHT, -2)
            .with(Signal.RECIRC, 1)
            .with(Signal.AC_MAX, 1)

        assertEquals(Temp.Lo, s.tempLeft)
        assertEquals(Temp.Lo, s.tempRight)
        assertTrue("the macro forces recirc on, and we must show it", s.recircOn)
        assertTrue(s.maxAcOn)
    }

    @Test
    fun `has distinguishes a missing signal from a zero one`() {
        val s = ClimateState.EMPTY.with(Signal.AC, 0)
        assertTrue(s.has(Signal.AC))
        assertFalse(s.has(Signal.AUTO))
        assertFalse(s.acOn)
    }
}

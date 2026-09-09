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
        // Illumination is the one nullable flag: absent is not "day".
        assertNull(s.isNight)
    }

    @Test
    fun `an empty state has no climate data`() {
        assertFalse(ClimateState.EMPTY.hasClimateData)
    }

    @Test
    fun `volume and illumination alone are not climate data`() {
        // The cold-start case exactly: SOUND and MAIN answer, CANBUS is silent.
        // Counting these would put the bar straight back to painting a
        // confident OFF on every climate control.
        val s = ClimateState.EMPTY
            .with(Signal.VOLUME, 10)
            .with(Signal.ILLUMINATION, 1)
            .with(Signal.TEMP_OUT, 0x10000744)
        assertFalse(s.isEmpty)
        assertFalse(s.hasClimateData)
    }

    @Test
    fun `any single climate signal is enough`() {
        for (signal in Signal.inModule(Signal.MODULE_CANBUS)) {
            // Value 0 as well as 1: a signal reporting zero is data, and it is
            // precisely the case `flag()` cannot tell from absence.
            assertTrue("$signal = 0 must count", ClimateState.EMPTY.with(signal, 0).hasClimateData)
            assertTrue("$signal = 1 must count", ClimateState.EMPTY.with(signal, 1).hasClimateData)
        }
    }

    @Test
    fun `the measured vehicle baseline has climate data`() {
        assertTrue(FakeVehicleBus.VEHICLE_BASELINE.hasClimateData)
    }

    @Test
    fun `with stores a raw value and derives from it`() {
        val s = ClimateState.EMPTY.with(Signal.TEMP_LEFT, 68)
        assertEquals(68, s[Signal.TEMP_LEFT])
        assertEquals(Temp.Degrees(68f, TempUnit.FAHRENHEIT), s.tempLeft)
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
        assertEquals(Temp.Degrees(69f, TempUnit.FAHRENHEIT), t.tempLeft)
        assertEquals("original must be untouched", Temp.Degrees(68f, TempUnit.FAHRENHEIT), s.tempLeft)
    }

    @Test
    fun `celsius mode decodes TEMP_LEFT as half-degrees`() {
        val s = ClimateState.EMPTY
            .with(Signal.TEMP_UNIT, 0)
            .with(Signal.TEMP_LEFT, 40)
        assertEquals(Temp.Degrees(20f, TempUnit.CELSIUS), s.tempLeft)
    }

    @Test
    fun `the measured vehicle baseline derives correctly`() {
        // The snapshot itself, not a hand-rolled near-copy of it: the fake bus
        // already owns the captured baseline, and a second transcription here
        // would only ever drift from it. (The first version of this test was
        // missing TEMP_UNIT and passed solely because TempUnit.from(null)
        // defaults to Fahrenheit.)
        val s = FakeVehicleBus.VEHICLE_BASELINE

        assertTrue(s.powerOn)
        assertTrue(s.acOn)
        assertTrue(s.autoOn)
        // The baseline's raw `SYNC to 1` is the vendor's DUAL flag, so under
        // the measured polarity this snapshot has the zones **independent**.
        //
        // Flagged tension: on 2026-09-08 a single driver `+` tap moved *both*
        // setpoints, which is synced behaviour. Either DUAL changed between
        // that capture and this snapshot, or one of the two was mis-recorded.
        // Asserting the derivation the raw value actually implies rather than
        // the one the story implies; worth one re-capture to settle.
        assertFalse(s.syncOn)
        assertFalse(s.recircOn)
        assertEquals(Fan.Auto, s.fan)
        assertEquals(Temp.Degrees(68f, TempUnit.FAHRENHEIT), s.tempLeft)
        assertEquals(Temp.Degrees(68f, TempUnit.FAHRENHEIT), s.tempRight)
        assertEquals(AirflowMode.NONE, s.airflow)
        assertEquals(SeatLevel.OFF, s.seatHeatL)
        assertFalse(s.wheelHeatOn)
        assertEquals(10, s.volume)
        assertEquals("illumination 0 is day", false, s.isNight)
    }

    @Test
    fun `illumination tells absent from zero from one`() {
        // The whole point of it being nullable. An absent illumination must be
        // distinguishable from a reported zero, because the palette treats the
        // two differently: unknown renders NIGHT, a reported zero renders DAY.
        assertNull("never reported", ClimateState.EMPTY.isNight)
        assertEquals("reported 0 is day", false, ClimateState.EMPTY.with(Signal.ILLUMINATION, 0).isNight)
        assertEquals("reported 1 is night", true, ClimateState.EMPTY.with(Signal.ILLUMINATION, 1).isNight)
    }

    @Test
    fun `an empty state reports every expected signal as missing`() {
        // Every signal we render. Diagnostics are excluded by design: they are
        // registered out of interest, and nagging for one the vehicle may not
        // have would run the re-registration retry to its cap every start.
        val expected = Signal.entries.filterNot { it.diagnostic }.toSet()
        assertEquals(expected, ClimateState.EMPTY.missingSignals)
    }

    @Test
    fun `a reported signal leaves the missing set, at zero as well as one`() {
        assertFalse(ClimateState.EMPTY.with(Signal.VOLUME, 0).missingSignals.contains(Signal.VOLUME))
        assertFalse(
            ClimateState.EMPTY.with(Signal.ILLUMINATION, 1).missingSignals
                .contains(Signal.ILLUMINATION),
        )
    }

    @Test
    fun `the measured vehicle baseline is missing only the outside temperature`() {
        // The baseline is the snapshot captured from the vehicle, and it does
        // NOT carry TEMP_OUT — that capture was stationary and the signal was
        // decoded later. So this asserts what is real rather than "nothing
        // missing": every signal the baseline holds is accounted for, and the
        // one gap is named, so adding TEMP_OUT to the baseline later fails
        // here loudly instead of silently.
        assertEquals(setOf(Signal.TEMP_OUT), FakeVehicleBus.VEHICLE_BASELINE.missingSignals)
    }

    @Test
    fun `the missing set is in declaration order, so a log line is stable`() {
        val missing = ClimateState.EMPTY.missingSignals.toList()
        assertEquals(Signal.entries.filterNot { it.diagnostic }, missing)
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

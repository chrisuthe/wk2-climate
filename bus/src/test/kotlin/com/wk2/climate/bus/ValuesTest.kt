package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Test

class ValuesTest {

    // ---- Temp ----

    @Test
    fun `a plain temperature reads as degrees`() {
        assertEquals(Temp.Degrees(68), Temp.from(68))
    }

    @Test
    fun `minus two is the LO sentinel, not a temperature below zero`() {
        assertEquals(Temp.Lo, Temp.from(-2))
    }

    @Test
    fun `other negatives are unavailable, never a number and never OFF`() {
        assertEquals(Temp.Unavailable, Temp.from(-1))
        assertEquals(Temp.Unavailable, Temp.from(-99))
    }

    @Test
    fun `a missing temperature is unavailable`() {
        assertEquals(Temp.Unavailable, Temp.from(null))
    }

    // ---- Fan ----

    @Test
    fun `fan levels one to seven read as levels`() {
        (1..7).forEach { assertEquals(Fan.Level(it), Fan.from(it)) }
    }

    @Test
    fun `fifteen is the AUTO sentinel, not a fan speed`() {
        // Measured on vehicle: with AUTO engaged the bus reports 15 while the
        // fan physically runs at 3. Rendering 15 as a level shows a speed that
        // does not exist.
        assertEquals(Fan.Auto, Fan.from(15))
    }

    @Test
    fun `fan level zero is a real level meaning off`() {
        assertEquals(Fan.Level(0), Fan.from(0))
    }

    @Test
    fun `fan values above the ceiling but below the sentinel are unavailable`() {
        assertEquals(Fan.Unavailable, Fan.from(8))
        assertEquals(Fan.Unavailable, Fan.from(14))
    }

    @Test
    fun `a missing fan value is unavailable`() {
        assertEquals(Fan.Unavailable, Fan.from(null))
    }

    @Test
    fun `the fan ceiling is seven`() {
        assertEquals(7, Fan.MAX_STEP)
    }

    // ---- SeatLevel ----

    @Test
    fun `seat levels map to the three presented states`() {
        assertEquals(SeatLevel.OFF, SeatLevel.from(0))
        assertEquals(SeatLevel.LOW, SeatLevel.from(1))
        assertEquals(SeatLevel.HIGH, SeatLevel.from(3))
    }

    @Test
    fun `seat level two maps to HIGH even though the cycle never reaches it`() {
        // Measured cycle is 0 -> 3 -> 1 -> 0; state 2 is never visited. Mapped
        // defensively because the cycle command is not necessarily the only writer.
        assertEquals(SeatLevel.HIGH, SeatLevel.from(2))
    }

    @Test
    fun `unknown seat values are unavailable`() {
        assertEquals(SeatLevel.UNAVAILABLE, SeatLevel.from(null))
        assertEquals(SeatLevel.UNAVAILABLE, SeatLevel.from(-1))
        assertEquals(SeatLevel.UNAVAILABLE, SeatLevel.from(4))
    }
}

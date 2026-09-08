package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Test

class ValuesTest {

    // ---- Temp ----

    @Test
    fun `a plain fahrenheit temperature reads as degrees`() {
        assertEquals(Temp.Degrees(68f, TempUnit.FAHRENHEIT), Temp.from(68, TempUnit.FAHRENHEIT))
    }

    @Test
    fun `a celsius temperature decodes from half-degrees`() {
        // Raw units are half-degrees Celsius: 40 -> 20.0 C.
        assertEquals(Temp.Degrees(20f, TempUnit.CELSIUS), Temp.from(40, TempUnit.CELSIUS))
    }

    @Test
    fun `minus two is the LO sentinel, not a temperature below zero`() {
        assertEquals(Temp.Lo, Temp.from(-2, TempUnit.FAHRENHEIT))
    }

    @Test
    fun `minus three is the HI sentinel, not a temperature`() {
        assertEquals(Temp.Hi, Temp.from(-3, TempUnit.FAHRENHEIT))
    }

    @Test
    fun `minus one and other negatives are unavailable, never a number and never OFF`() {
        assertEquals(Temp.Unavailable, Temp.from(-1, TempUnit.FAHRENHEIT))
        assertEquals(Temp.Unavailable, Temp.from(-99, TempUnit.FAHRENHEIT))
    }

    @Test
    fun `a missing temperature is unavailable`() {
        assertEquals(Temp.Unavailable, Temp.from(null, TempUnit.FAHRENHEIT))
    }

    @Test
    fun `raw values outside the protocol window are unavailable, in both units`() {
        assertEquals(Temp.Unavailable, Temp.from(29, TempUnit.FAHRENHEIT))
        assertEquals(Temp.Unavailable, Temp.from(29, TempUnit.CELSIUS))
        assertEquals(Temp.Unavailable, Temp.from(129, TempUnit.FAHRENHEIT))
        assertEquals(Temp.Unavailable, Temp.from(129, TempUnit.CELSIUS))
    }

    @Test
    fun `raw values at the protocol window boundary decode, in both units`() {
        assertEquals(Temp.Degrees(30f, TempUnit.FAHRENHEIT), Temp.from(30, TempUnit.FAHRENHEIT))
        assertEquals(Temp.Degrees(15f, TempUnit.CELSIUS), Temp.from(30, TempUnit.CELSIUS))
        assertEquals(Temp.Degrees(128f, TempUnit.FAHRENHEIT), Temp.from(128, TempUnit.FAHRENHEIT))
        assertEquals(Temp.Degrees(64f, TempUnit.CELSIUS), Temp.from(128, TempUnit.CELSIUS))
    }

    // ---- TempUnit ----

    @Test
    fun `temp unit zero is celsius, everything else including missing is fahrenheit`() {
        // A missing U_AIR_TEMP_UNIT defaults to FAHRENHEIT deliberately: this
        // vehicle reports 1, and guessing CELSIUS would silently halve every
        // reading instead of showing degrees correctly.
        assertEquals(TempUnit.CELSIUS, TempUnit.from(0))
        assertEquals(TempUnit.FAHRENHEIT, TempUnit.from(1))
        assertEquals(TempUnit.FAHRENHEIT, TempUnit.from(null))
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

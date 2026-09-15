package com.wk2.climate.ui

import com.wk2.climate.bus.SeatLevel
import com.wk2.climate.ui.bar.SeatIndicator
import com.wk2.climate.ui.bar.SeatIndicator.Kind
import org.junit.Assert.assertEquals
import org.junit.Test

class SeatIndicatorTest {

    @Test
    fun `heat LOW is one warm pip and heat HIGH is two`() {
        assertEquals(SeatIndicator(Kind.HEAT, 1), SeatIndicator.of(SeatLevel.LOW, SeatLevel.OFF))
        assertEquals(SeatIndicator(Kind.HEAT, 2), SeatIndicator.of(SeatLevel.HIGH, SeatLevel.OFF))
    }

    @Test
    fun `cool LOW is one cool pip and cool HIGH is two`() {
        assertEquals(SeatIndicator(Kind.COOL, 1), SeatIndicator.of(SeatLevel.OFF, SeatLevel.LOW))
        assertEquals(SeatIndicator(Kind.COOL, 2), SeatIndicator.of(SeatLevel.OFF, SeatLevel.HIGH))
    }

    @Test
    fun `both reported off is OFF`() {
        assertEquals(SeatIndicator(Kind.OFF, 0), SeatIndicator.of(SeatLevel.OFF, SeatLevel.OFF))
    }

    @Test
    fun `both unreported is UNKNOWN, never OFF`() {
        assertEquals(SeatIndicator(Kind.UNKNOWN, 0), SeatIndicator.of(SeatLevel.UNAVAILABLE, SeatLevel.UNAVAILABLE))
    }

    @Test
    fun `one off and the other unreported is still UNKNOWN`() {
        assertEquals(SeatIndicator(Kind.UNKNOWN, 0), SeatIndicator.of(SeatLevel.OFF, SeatLevel.UNAVAILABLE))
        assertEquals(SeatIndicator(Kind.UNKNOWN, 0), SeatIndicator.of(SeatLevel.UNAVAILABLE, SeatLevel.OFF))
    }

    @Test
    fun `a reported level wins over an unreported one`() {
        assertEquals(SeatIndicator(Kind.HEAT, 2), SeatIndicator.of(SeatLevel.HIGH, SeatLevel.UNAVAILABLE))
        assertEquals(SeatIndicator(Kind.COOL, 1), SeatIndicator.of(SeatLevel.UNAVAILABLE, SeatLevel.LOW))
    }

    @Test
    fun `heat and cool both on renders heat, at heat's level`() {
        // The protocol makes them mutually exclusive; this can only arrive transiently.
        assertEquals(SeatIndicator(Kind.HEAT, 2), SeatIndicator.of(SeatLevel.HIGH, SeatLevel.LOW))
        assertEquals(SeatIndicator(Kind.HEAT, 1), SeatIndicator.of(SeatLevel.LOW, SeatLevel.HIGH))
    }

    @Test
    fun `every combination lights pips only when heat or cool is shown`() {
        for (heat in SeatLevel.entries) for (vent in SeatLevel.entries) {
            val i = SeatIndicator.of(heat, vent)
            val expectedKind = when {
                heat.isOn -> Kind.HEAT
                vent.isOn -> Kind.COOL
                heat == SeatLevel.OFF && vent == SeatLevel.OFF -> Kind.OFF
                else -> Kind.UNKNOWN
            }
            assertEquals("$heat / $vent", expectedKind, i.kind)
            val expectedPips = when (i.kind) {
                Kind.HEAT -> heat.litPips
                Kind.COOL -> vent.litPips
                Kind.OFF, Kind.UNKNOWN -> 0
            }
            assertEquals("$heat / $vent", expectedPips, i.litPips)
        }
    }
}

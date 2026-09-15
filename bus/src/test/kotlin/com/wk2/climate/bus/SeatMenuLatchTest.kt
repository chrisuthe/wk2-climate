package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeatMenuLatchTest {

    private val window = SeatMenuLatch.DEFAULT_RETAP_WINDOW_MS

    @Test
    fun `starts with no menu open`() {
        assertNull(SeatMenuLatch().open)
    }

    @Test
    fun `a tap on a seat button opens that side`() {
        val latch = SeatMenuLatch()
        assertEquals(SeatSide.DRIVER, latch.onButtonTap(SeatSide.DRIVER, 0))
        assertEquals(SeatSide.DRIVER, latch.open)
    }

    @Test
    fun `a tap on the other seat button switches sides`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        assertEquals(SeatSide.PASSENGER, latch.onButtonTap(SeatSide.PASSENGER, 1_000))
    }

    @Test
    fun `a tap on the open side's own button with no outside touch first closes it`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        assertNull(latch.onButtonTap(SeatSide.DRIVER, 1_000))
        assertNull(latch.open)
    }

    @Test
    fun `an outside touch closes the menu`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        assertNull(latch.onOutsideTouch(1_000))
        assertNull(latch.open)
    }

    @Test
    fun `an outside touch with nothing open is a no-op`() {
        val latch = SeatMenuLatch()
        assertNull(latch.onOutsideTouch(0))
        assertNull(latch.open)
    }

    @Test
    fun `re-tap race - the own-button click that follows the outside touch inside the window is the same tap and stays closed`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        latch.onOutsideTouch(5_000)                  // the DOWN, seen by the menu window
        assertNull(latch.onButtonTap(SeatSide.DRIVER, 5_000 + window - 1))   // the UP, seen by the bar
        assertNull(latch.open)
    }

    @Test
    fun `an own-button tap after the window is a fresh tap and reopens`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        latch.onOutsideTouch(5_000)
        assertEquals(SeatSide.DRIVER, latch.onButtonTap(SeatSide.DRIVER, 5_000 + window))
    }

    @Test
    fun `the other side's button inside the window is a switch, not a swallowed re-tap`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        latch.onOutsideTouch(5_000)
        assertEquals(SeatSide.PASSENGER, latch.onButtonTap(SeatSide.PASSENGER, 5_000 + 50))
    }

    @Test
    fun `a swallowed re-tap consumes the record so an immediate second tap opens`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        latch.onOutsideTouch(5_000)
        latch.onButtonTap(SeatSide.DRIVER, 5_050)    // swallowed
        // The menu is closed, so no window exists and no ACTION_OUTSIDE precedes this click.
        assertEquals(SeatSide.DRIVER, latch.onButtonTap(SeatSide.DRIVER, 5_200))
    }

    @Test
    fun `close is not an outside touch - a tap right after it opens`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        assertNull(latch.close())
        assertEquals(SeatSide.DRIVER, latch.onButtonTap(SeatSide.DRIVER, 10))
    }

    @Test
    fun `an outside touch on app content followed much later by a tap opens normally`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.PASSENGER, 0)
        latch.onOutsideTouch(1_000)
        assertEquals(SeatSide.PASSENGER, latch.onButtonTap(SeatSide.PASSENGER, 60_000))
    }

    @Test
    fun `a second outside touch with nothing open keeps the first one's record`() {
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        latch.onOutsideTouch(5_000)
        assertNull(latch.onOutsideTouch(5_100))
        // Still inside the window measured from the first touch, so still the same tap.
        assertNull(latch.onButtonTap(SeatSide.DRIVER, 5_000 + window - 1))
    }

    @Test
    fun `close with nothing open is a no-op`() {
        val latch = SeatMenuLatch()
        assertNull(latch.close())
        assertNull(latch.open)
    }

    @Test
    fun `an outside touch on any other control followed by an own-side tap inside the window is swallowed`() {
        // The spec's rule is keyed on side and time only. Tapping AUTO with the
        // driver menu open, then the driver button within the window, drops that
        // tap; the driver taps again. A known trade, documented here.
        val latch = SeatMenuLatch()
        latch.onButtonTap(SeatSide.DRIVER, 0)
        latch.onOutsideTouch(5_000)                  // the AUTO tap
        assertNull(latch.onButtonTap(SeatSide.DRIVER, 5_000 + window / 2))
        assertEquals(SeatSide.DRIVER, latch.onButtonTap(SeatSide.DRIVER, 5_000 + window / 2 + 10))
    }
}

package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveSlotTest {

    private val dwell = 30_000L

    @Test
    fun `it starts on the mild band, the safe middle`() {
        assertEquals(SlotBand.MILD, AdaptiveSlot().band)
    }

    @Test
    fun `a null outside temperature pins to the mild band`() {
        // The fallback for an invalid, absent or non-Fahrenheit U_TEMP_OUT.
        // Neither cell may be blank and the geometry must never change.
        val slot = AdaptiveSlot()
        assertEquals(SlotBand.MILD, slot.update(null, 0))
        assertEquals(SlotBand.MILD, slot.update(null, 10 * dwell))
    }

    @Test
    fun `below freezing promotes the cold band`() {
        val slot = AdaptiveSlot()
        assertEquals(SlotBand.COLD, slot.update(20, dwell + 1))
    }

    @Test
    fun `hot promotes the hot band`() {
        val slot = AdaptiveSlot()
        assertEquals(SlotBand.HOT, slot.update(95, dwell + 1))
    }

    @Test
    fun `the comfortable band holds mild`() {
        val slot = AdaptiveSlot()
        assertEquals(SlotBand.MILD, slot.update(60, dwell + 1))
    }

    // ---- the pairings ----
    //
    // The owner's table, one test per row. These are the mapping from band to
    // pair, and nothing else in the app is allowed to restate it.

    @Test
    fun `the cold band pairs front defrost with seat heat`() {
        val slot = AdaptiveSlot()
        val band = slot.update(20, dwell + 1)
        assertEquals(SlotContent.FRONT_DEFROST, band.first)
        assertEquals(SlotContent.SEAT_HEAT, band.second)
    }

    @Test
    fun `the mild band pairs the heated wheel with seat heat`() {
        val slot = AdaptiveSlot()
        val band = slot.update(60, dwell + 1)
        assertEquals(SlotContent.WHEEL, band.first)
        assertEquals(SlotContent.SEAT_HEAT, band.second)
    }

    @Test
    fun `the hot band pairs seat cool with max a c`() {
        val slot = AdaptiveSlot()
        val band = slot.update(95, dwell + 1)
        assertEquals(SlotContent.SEAT_COOL, band.first)
        assertEquals(SlotContent.MAX_AC, band.second)
    }

    @Test
    fun `both cells flip together, so a mismatched pair is never observable`() {
        // The band *is* the pair, which is why one state machine drives both
        // cells: there is no intermediate value in which the first cell has
        // moved and the second has not.
        val slot = AdaptiveSlot()
        var t = dwell + 1
        assertEquals(SlotBand.COLD, slot.update(20, t))
        assertEquals(SlotContent.FRONT_DEFROST, slot.band.first)
        assertEquals(SlotContent.SEAT_HEAT, slot.band.second)

        t += dwell + 1
        assertEquals(SlotBand.HOT, slot.update(95, t))
        assertEquals(SlotContent.SEAT_COOL, slot.band.first)
        assertEquals(SlotContent.MAX_AC, slot.band.second)
    }

    // ---- hysteresis ----

    @Test
    fun `a vehicle sitting exactly at freezing does not flip back and forth`() {
        // The whole point of the deadband. Without it, 32 / 31 / 32 / 31 would
        // swap both controls under the driver's thumb repeatedly.
        val slot = AdaptiveSlot()
        var t = dwell + 1
        assertEquals(SlotBand.COLD, slot.update(31, t))

        // Crossing back above 32 is not enough; it must clear 32 + 3.
        t += dwell + 1
        assertEquals(SlotBand.COLD, slot.update(33, t))
        t += dwell + 1
        assertEquals(SlotBand.COLD, slot.update(34, t))
        t += dwell + 1
        assertEquals(SlotBand.MILD, slot.update(36, t))
    }

    @Test
    fun `leaving the hot band requires clearing the deadband downward`() {
        val slot = AdaptiveSlot()
        var t = dwell + 1
        assertEquals(SlotBand.HOT, slot.update(90, t))
        t += dwell + 1
        assertEquals("84 is inside the deadband", SlotBand.HOT, slot.update(84, t))
        t += dwell + 1
        assertEquals(SlotBand.MILD, slot.update(81, t))
    }

    @Test
    fun `entering the cold band happens at the nominal 32 F, not 29`() {
        // The deadband is applied on **exit only**. Applying it on entry too
        // would push this threshold to 29 F -- a real review finding once.
        assertEquals(SlotBand.MILD, AdaptiveSlot().update(32, dwell + 1))
        assertEquals(SlotBand.COLD, AdaptiveSlot().update(31, dwell + 1))
        // 30 and 29 sit inside what a doubled deadband would have swallowed,
        // so they must still promote.
        assertEquals(SlotBand.COLD, AdaptiveSlot().update(30, dwell + 1))
        assertEquals(SlotBand.COLD, AdaptiveSlot().update(29, dwell + 1))
    }

    @Test
    fun `entering the hot band happens at the nominal 85 F, not 88`() {
        assertEquals(SlotBand.MILD, AdaptiveSlot().update(85, dwell + 1))
        assertEquals(SlotBand.HOT, AdaptiveSlot().update(86, dwell + 1))
        assertEquals(SlotBand.HOT, AdaptiveSlot().update(87, dwell + 1))
    }

    @Test
    fun `leaving the cold band requires clearing the deadband upward`() {
        // The mirror of the hot-band exit test, so both directions are pinned.
        val slot = AdaptiveSlot(initial = SlotBand.COLD)
        var t = dwell + 1
        assertEquals("34 is inside the deadband", SlotBand.COLD, slot.update(34, t))
        t += dwell + 1
        assertEquals(SlotBand.MILD, slot.update(35, t))
    }

    // ---- dwell ----

    @Test
    fun `it will not change again inside the dwell window`() {
        val slot = AdaptiveSlot()
        val t = dwell + 1
        assertEquals(SlotBand.COLD, slot.update(20, t))

        // A big swing arriving too soon is held.
        assertEquals(SlotBand.COLD, slot.update(95, t + 1_000))
        assertEquals(SlotBand.COLD, slot.update(95, t + dwell - 1))
        assertEquals(SlotBand.HOT, slot.update(95, t + dwell + 1))
    }

    @Test
    fun `the dwell window admits exactly one change`() {
        val slot = AdaptiveSlot()
        var t = dwell + 1
        assertEquals(SlotBand.COLD, slot.update(20, t))
        // Three further swings inside one window, all refused.
        assertEquals(SlotBand.COLD, slot.update(95, t + 5_000))
        assertEquals(SlotBand.COLD, slot.update(60, t + 10_000))
        assertEquals(SlotBand.COLD, slot.update(95, t + 15_000))
        // And the pair is still the cold pair, not half-moved.
        assertEquals(SlotContent.FRONT_DEFROST, slot.band.first)
        assertEquals(SlotContent.SEAT_HEAT, slot.band.second)

        t += dwell + 1
        assertEquals(SlotBand.HOT, slot.update(95, t))
    }

    @Test
    fun `an unchanged target does not restart the dwell clock`() {
        val slot = AdaptiveSlot()
        val t = dwell + 1
        assertEquals(SlotBand.COLD, slot.update(20, t))
        // Repeatedly reporting the same band must not extend the lockout.
        repeat(5) { slot.update(20, t + it * 100L) }
        assertEquals(SlotBand.HOT, slot.update(95, t + dwell + 1))
    }

    // ---- touch safety ----

    @Test
    fun `it never changes while a finger is down on the first cell`() {
        val slot = AdaptiveSlot()
        slot.onFingerDown(SlotCell.FIRST)
        assertEquals(SlotBand.MILD, slot.update(20, 10 * dwell))
        slot.onFingerUp(SlotCell.FIRST)
        assertEquals(SlotBand.COLD, slot.update(20, 20 * dwell))
    }

    @Test
    fun `it never changes while a finger is down on the second cell`() {
        // Both cells move at once, so a finger on either freezes the pair --
        // otherwise SEAT HEAT could turn into MAX A C under the thumb using it.
        val slot = AdaptiveSlot()
        slot.onFingerDown(SlotCell.SECOND)
        assertEquals(SlotBand.MILD, slot.update(95, 10 * dwell))
        slot.onFingerUp(SlotCell.SECOND)
        assertEquals(SlotBand.HOT, slot.update(95, 20 * dwell))
    }

    @Test
    fun `one cell's release does not unfreeze the pair while the other is held`() {
        // A single boolean would let the first release clear the freeze the
        // second finger is still relying on.
        val slot = AdaptiveSlot()
        slot.onFingerDown(SlotCell.FIRST)
        slot.onFingerDown(SlotCell.SECOND)
        slot.onFingerUp(SlotCell.FIRST)
        assertEquals(SlotBand.MILD, slot.update(20, 10 * dwell))
        slot.onFingerUp(SlotCell.SECOND)
        assertEquals(SlotBand.COLD, slot.update(20, 20 * dwell))
    }

    @Test
    fun `a band change arriving under a finger lands only once it lifts`() {
        val slot = AdaptiveSlot()
        slot.onFingerDown(SlotCell.SECOND)
        val t = 10 * dwell
        // The whole press, polled repeatedly, moves nothing.
        repeat(5) { assertEquals(SlotBand.MILD, slot.update(95, t + it * 1_000L)) }
        assertEquals(SlotContent.WHEEL, slot.band.first)
        assertEquals(SlotContent.SEAT_HEAT, slot.band.second)

        slot.onFingerUp(SlotCell.SECOND)
        assertEquals(SlotBand.HOT, slot.update(95, t + 10_000L))
        assertEquals(SlotContent.SEAT_COOL, slot.band.first)
        assertEquals(SlotContent.MAX_AC, slot.band.second)
    }

    @Test
    fun `a duplicated finger down does not leave the pair frozen forever`() {
        // Set semantics, not a counter: a dropped or repeated pointer event
        // must not strand the machine with a finger it thinks is still there.
        val slot = AdaptiveSlot()
        slot.onFingerDown(SlotCell.FIRST)
        slot.onFingerDown(SlotCell.FIRST)
        slot.onFingerUp(SlotCell.FIRST)
        assertEquals(SlotBand.COLD, slot.update(20, 10 * dwell))
    }

    @Test
    fun `it never changes within a second of a tap`() {
        // Swapping the controls immediately after one is used would mean the
        // driver's next tap hits something they did not aim at.
        val slot = AdaptiveSlot()
        val t = 10 * dwell
        slot.onTap(t)
        assertEquals(SlotBand.MILD, slot.update(20, t + 500))
        assertEquals(SlotBand.MILD, slot.update(20, t + 999))
        assertEquals(SlotBand.COLD, slot.update(20, t + 1_001))
    }

    @Test
    fun `a tap freezes both cells, not just the one that was tapped`() {
        // The lockout is deliberately not per-cell. A driver who has just
        // pressed SEAT HEAT is about to press it again or press its neighbour,
        // and a swap in that instant moves both.
        val slot = AdaptiveSlot()
        val t = 10 * dwell
        slot.onTap(t)
        assertEquals(SlotBand.MILD, slot.update(95, t + 500))
        assertEquals(SlotContent.WHEEL, slot.band.first)
        assertEquals(SlotContent.SEAT_HEAT, slot.band.second)
        assertEquals(SlotBand.HOT, slot.update(95, t + 1_001))
    }

    @Test
    fun `defrost outranks comfort even in a cold swing from hot`() {
        val slot = AdaptiveSlot(initial = SlotBand.HOT)
        assertEquals(SlotBand.COLD, slot.update(10, dwell + 1))
        assertEquals(SlotContent.FRONT_DEFROST, slot.band.first)
    }
}

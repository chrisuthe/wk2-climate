package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveSlotTest {

    private val dwell = 30_000L

    @Test
    fun `it starts on seat heat, the safe middle band`() {
        assertEquals(SlotContent.SEAT_HEAT, AdaptiveSlot().content)
    }

    @Test
    fun `a null outside temperature pins to seat heat`() {
        // This is the shipping path until U_TEMP_OUT is decoded. The slot must
        // never be blank and the geometry must never change.
        val slot = AdaptiveSlot()
        assertEquals(SlotContent.SEAT_HEAT, slot.update(null, 0))
        assertEquals(SlotContent.SEAT_HEAT, slot.update(null, 10 * dwell))
    }

    @Test
    fun `below freezing promotes front defrost`() {
        val slot = AdaptiveSlot()
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(20, dwell + 1))
    }

    @Test
    fun `hot promotes seat cool`() {
        val slot = AdaptiveSlot()
        assertEquals(SlotContent.SEAT_COOL, slot.update(95, dwell + 1))
    }

    @Test
    fun `the comfortable band holds seat heat`() {
        val slot = AdaptiveSlot()
        assertEquals(SlotContent.SEAT_HEAT, slot.update(60, dwell + 1))
    }

    // ---- hysteresis ----

    @Test
    fun `a vehicle sitting exactly at freezing does not flip back and forth`() {
        // The whole point of the deadband. Without it, 32 / 31 / 32 / 31 would
        // swap the control under the driver's thumb repeatedly.
        val slot = AdaptiveSlot()
        var t = dwell + 1
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(31, t))

        // Crossing back above 32 is not enough; it must clear 32 + 3.
        t += dwell + 1
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(33, t))
        t += dwell + 1
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(34, t))
        t += dwell + 1
        assertEquals(SlotContent.SEAT_HEAT, slot.update(36, t))
    }

    @Test
    fun `leaving seat cool requires clearing the deadband downward`() {
        val slot = AdaptiveSlot()
        var t = dwell + 1
        assertEquals(SlotContent.SEAT_COOL, slot.update(90, t))
        t += dwell + 1
        assertEquals("84 is inside the deadband", SlotContent.SEAT_COOL, slot.update(84, t))
        t += dwell + 1
        assertEquals(SlotContent.SEAT_HEAT, slot.update(81, t))
    }

    // ---- dwell ----

    @Test
    fun `it will not change again inside the dwell window`() {
        val slot = AdaptiveSlot()
        val t = dwell + 1
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(20, t))

        // A big swing arriving too soon is held.
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(95, t + 1_000))
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(95, t + dwell - 1))
        assertEquals(SlotContent.SEAT_COOL, slot.update(95, t + dwell + 1))
    }

    // ---- touch safety ----

    @Test
    fun `it never changes while a finger is down`() {
        val slot = AdaptiveSlot()
        slot.onFingerDown()
        assertEquals(SlotContent.SEAT_HEAT, slot.update(20, 10 * dwell))
        slot.onFingerUp()
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(20, 20 * dwell))
    }

    @Test
    fun `it never changes within a second of a tap`() {
        // Swapping the control immediately after it is used would mean the
        // driver's next tap hits something they did not aim at.
        val slot = AdaptiveSlot()
        val t = 10 * dwell
        slot.onTap(t)
        assertEquals(SlotContent.SEAT_HEAT, slot.update(20, t + 500))
        assertEquals(SlotContent.SEAT_HEAT, slot.update(20, t + 999))
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(20, t + 1_001))
    }

    @Test
    fun `defrost outranks comfort even in a hot swing from cold`() {
        val slot = AdaptiveSlot(initial = SlotContent.SEAT_COOL)
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(10, dwell + 1))
    }

    @Test
    fun `an unchanged target does not restart the dwell clock`() {
        val slot = AdaptiveSlot()
        val t = dwell + 1
        assertEquals(SlotContent.FRONT_DEFROST, slot.update(20, t))
        // Repeatedly reporting the same band must not extend the lockout.
        repeat(5) { slot.update(20, t + it * 100L) }
        assertEquals(SlotContent.SEAT_COOL, slot.update(95, t + dwell + 1))
    }
}

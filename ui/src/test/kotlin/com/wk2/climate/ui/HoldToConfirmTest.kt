package com.wk2.climate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldToConfirmTest {

    private val required = 800L

    @Test
    fun `a tap makes no progress worth showing and never confirms`() {
        // The whole point: this vehicle has no physical HVAC controls, so a
        // stray tap must not be able to power climate off.
        assertFalse(HoldToConfirm.isConfirmed(0, required))
        assertFalse(HoldToConfirm.isConfirmed(50, required))
        assertFalse(HoldToConfirm.isConfirmed(required - 1, required))
    }

    @Test
    fun `it confirms exactly at the required hold`() {
        assertTrue(HoldToConfirm.isConfirmed(required, required))
        assertTrue(HoldToConfirm.isConfirmed(required + 100, required))
    }

    @Test
    fun `progress runs zero to one across the hold`() {
        assertEquals(0f, HoldToConfirm.progressAt(0, required), 0.001f)
        assertEquals(0.5f, HoldToConfirm.progressAt(400, required), 0.001f)
        assertEquals(1f, HoldToConfirm.progressAt(800, required), 0.001f)
    }

    @Test
    fun `progress is clamped past the hold`() {
        assertEquals(1f, HoldToConfirm.progressAt(5_000, required), 0.001f)
    }

    @Test
    fun `negative elapsed time is treated as no progress`() {
        assertEquals(0f, HoldToConfirm.progressAt(-100, required), 0.001f)
        assertFalse(HoldToConfirm.isConfirmed(-100, required))
    }

    @Test
    fun `a zero or negative requirement never confirms by accident`() {
        // Guards against a misconfiguration turning the destructive control
        // into a single tap.
        assertFalse(HoldToConfirm.isConfirmed(0, 0))
        assertFalse(HoldToConfirm.isConfirmed(10, 0))
        assertEquals(0f, HoldToConfirm.progressAt(10, 0), 0.001f)
    }
}

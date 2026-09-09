package com.wk2.climate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldToConfirmTest {

    private val required = 800L

    // A second, deliberately different requirement. Every boundary is asserted
    // against both, so an implementation that ignored `requiredMs` and hardcoded
    // 800 fails here -- and `requiredMs` is the one parameter the extraction of
    // HoldToConfirm existed to make variable.
    private val shorter = 400L

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
    fun `progress never leaves zero to one for any input`() {
        val heldValues = listOf(-10_000L, -1L, 0L, 1L, 199L, 400L, 401L, 5_000L, Long.MAX_VALUE)
        val requiredValues = listOf(-1L, 0L, 1L, shorter, required)
        for (held in heldValues) {
            for (req in requiredValues) {
                val progress = HoldToConfirm.progressAt(held, req)
                assertTrue(
                    "progressAt($held, $req) = $progress is outside 0f..1f",
                    progress in 0f..1f,
                )
            }
        }
    }

    @Test
    fun `the boundaries move with the requirement`() {
        // The same three boundaries as above, on a requirement half as long.
        assertFalse(HoldToConfirm.isConfirmed(shorter - 1, shorter))
        assertTrue(HoldToConfirm.isConfirmed(shorter, shorter))
        assertTrue(HoldToConfirm.isConfirmed(shorter + 1, shorter))
        assertEquals(0.5f, HoldToConfirm.progressAt(200, shorter), 0.001f)
        assertEquals(1f, HoldToConfirm.progressAt(400, shorter), 0.001f)

        // And the crossover: 500ms confirms a 400ms hold but not an 800ms one,
        // which no hardcoded requirement can satisfy both ways.
        assertTrue(HoldToConfirm.isConfirmed(500, shorter))
        assertFalse(HoldToConfirm.isConfirmed(500, required))
        assertEquals(1f, HoldToConfirm.progressAt(500, shorter), 0.001f)
        assertEquals(0.625f, HoldToConfirm.progressAt(500, required), 0.001f)
    }

    @Test
    fun `negative elapsed time is treated as no progress`() {
        assertEquals(0f, HoldToConfirm.progressAt(-100, required), 0.001f)
        assertFalse(HoldToConfirm.isConfirmed(-100, required))
        assertEquals(0f, HoldToConfirm.progressAt(-100, shorter), 0.001f)
        assertFalse(HoldToConfirm.isConfirmed(-100, shorter))
    }

    @Test
    fun `a zero or negative requirement never confirms by accident`() {
        // Guards against a misconfiguration turning the destructive control
        // into a single tap.
        assertFalse(HoldToConfirm.isConfirmed(0, 0))
        assertFalse(HoldToConfirm.isConfirmed(10, 0))
        assertEquals(0f, HoldToConfirm.progressAt(10, 0), 0.001f)

        // The negative half the name promises: fail closed, not open.
        assertFalse(HoldToConfirm.isConfirmed(0, -1))
        assertFalse(HoldToConfirm.isConfirmed(10, -1))
        assertFalse(HoldToConfirm.isConfirmed(Long.MAX_VALUE, -800))
        assertEquals(0f, HoldToConfirm.progressAt(10, -1), 0.001f)
        assertEquals(0f, HoldToConfirm.progressAt(10, -800), 0.001f)
    }
}

package com.wk2.climate.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The plain seat is the seat-outline subpath that opens each cool-seat glyph,
 * so the silhouette is identical to the heat and cool variants by
 * construction. These pin that: the plain path must be a closed prefix of the
 * cool path, and what follows it must be the snowflake's relative moveto.
 */
class GlyphsTest {

    @Test
    fun `the plain left seat is exactly the cool left seat's outline subpath`() {
        assertTrue(SEAT_PLAIN_LEFT.endsWith("z"))
        assertTrue(SEAT_COOL_LEFT.startsWith(SEAT_PLAIN_LEFT))
        assertEquals('m', SEAT_COOL_LEFT[SEAT_PLAIN_LEFT.length])
    }

    @Test
    fun `the plain right seat is exactly the cool right seat's outline subpath`() {
        assertTrue(SEAT_PLAIN_RIGHT.endsWith("z"))
        assertTrue(SEAT_COOL_RIGHT.startsWith(SEAT_PLAIN_RIGHT))
        assertEquals('m', SEAT_COOL_RIGHT[SEAT_PLAIN_RIGHT.length])
    }
}

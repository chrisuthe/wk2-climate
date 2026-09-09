package com.wk2.climate.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.wk2.climate.design.Palette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the *polarity* of the raised and inset surface overlays.
 *
 * The handoff's `surface-raised` token is an overlay, not a colour, so the two
 * palettes cannot share a literal: a 5% white wash recesses a panel on a dark
 * ground and glares on a light one. `Palette.DAY.surfaceRaised` was once
 * opaque `#ffffff`, which made the fan meter and the four seat tiles the
 * brightest things on an off-white page — the exact inversion of the depth
 * they are meant to carry, and it shipped in the bar.
 *
 * The assertions are about the *relationship* between a palette's overlay and
 * its own ground, never between one literal and another, so they survive a
 * retune of either value and fail the moment a polarity flips or an overlay
 * goes opaque.
 */
class SurfaceOverlayPolarityTest {

    @Test
    fun `both palettes keep the raised surface translucent`() {
        assertTrue("NIGHT.surfaceRaised must stay translucent", Palette.NIGHT.surfaceRaised.alpha < 1f)
        assertTrue("DAY.surfaceRaised must stay translucent", Palette.DAY.surfaceRaised.alpha < 1f)
    }

    @Test
    fun `the raised surface recesses against its own ground`() {
        assertTrue(
            "NIGHT's raised surface must land lighter than NIGHT's own ground",
            Palette.NIGHT.surfaceRaised.over(Palette.NIGHT.surface).luminance() >
                Palette.NIGHT.surface.luminance(),
        )
        assertTrue(
            "DAY's raised surface must land darker than DAY's own ground — a " +
                "white overlay glares on a light ground instead of recessing",
            Palette.DAY.surfaceRaised.over(Palette.DAY.surface).luminance() <
                Palette.DAY.surface.luminance(),
        )
    }

    @Test
    fun `the inset surface moves the ground the same way the raised one does`() {
        listOf(Palette.NIGHT, Palette.DAY).forEach { palette ->
            val ground = palette.surface.luminance()
            val raised = palette.surfaceRaised.over(palette.surface).luminance()
            val inset = palette.surfaceInset.over(palette.surface).luminance()
            assertEquals(
                "raised and inset must recess in the same direction",
                Math.signum(raised - ground),
                Math.signum(inset - ground),
                0f,
            )
        }
    }
}

/** A straight source-over composite, so an overlay can be judged against its ground. */
private fun Color.over(ground: Color): Color = Color(
    red = red * alpha + ground.red * (1f - alpha),
    green = green * alpha + ground.green * (1f - alpha),
    blue = blue * alpha + ground.blue * (1f - alpha),
)

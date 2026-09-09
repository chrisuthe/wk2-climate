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
    fun `the raised overlay moves its own ground toward its own ink`() {
        assertMovesGroundTowardInk("NIGHT.surfaceRaised", Palette.NIGHT) { it.surfaceRaised }
        assertMovesGroundTowardInk("DAY.surfaceRaised", Palette.DAY) { it.surfaceRaised }
    }

    @Test
    fun `the inset overlay moves its own ground toward its own ink`() {
        assertMovesGroundTowardInk("NIGHT.surfaceInset", Palette.NIGHT) { it.surfaceInset }
        assertMovesGroundTowardInk("DAY.surfaceInset", Palette.DAY) { it.surfaceInset }
    }

    /**
     * The relationship the day palette used to lose by holding one literal in
     * both tokens: an inset is a *lighter* overlay than a raised one, in both
     * palettes, so it must move its own ground less far.
     *
     * Stated as a distance rather than a direction, so it is one assertion for
     * a palette that lightens and a palette that darkens, and so it survives a
     * retune of either value. It is the assertion the previous three could not
     * make while `DAY.surfaceInset` was a copy of `DAY.surfaceRaised` — that
     * comparison was a quantity against itself and could only ever have failed
     * on NIGHT.
     */
    @Test
    fun `the inset overlay is the lighter of the two in both palettes`() {
        assertInsetIsLighter("NIGHT", Palette.NIGHT)
        assertInsetIsLighter("DAY", Palette.DAY)
    }
}

private fun assertInsetIsLighter(name: String, palette: Palette) {
    val ground = palette.surface.luminance()
    val raised = Math.abs(palette.surfaceRaised.over(palette.surface).luminance() - ground)
    val inset = Math.abs(palette.surfaceInset.over(palette.surface).luminance() - ground)
    assertTrue(
        "$name.surfaceInset must move its ground less far than $name.surfaceRaised " +
            "(raised=$raised, inset=$inset)",
        inset < raised,
    )
}

/**
 * The invariant both overlays share, stated so it is meaningful for either
 * palette: an overlay must composite to a luminance on the *ink's* side of its
 * own ground.
 *
 * That is what "recessed" means on both grounds without naming a direction —
 * night's ink is light and its overlay lightens, day's ink is dark and its
 * overlay darkens. It is deliberately not "raised and inset agree with each
 * other", which would say nothing about either one's polarity; their
 * *relative* magnitudes are pinned separately, by the fourth case.
 */
private fun assertMovesGroundTowardInk(
    name: String,
    palette: Palette,
    overlay: (Palette) -> Color,
) {
    val ground = palette.surface.luminance()
    val toInk = Math.signum(palette.ink.luminance() - ground)
    val moved = Math.signum(overlay(palette).over(palette.surface).luminance() - ground)
    assertEquals(
        "$name must move its own ground toward its own ink, not away from it",
        toInk,
        moved,
        0f,
    )
}

/** A straight source-over composite, so an overlay can be judged against its ground. */
private fun Color.over(ground: Color): Color = Color(
    red = red * alpha + ground.red * (1f - alpha),
    green = green * alpha + ground.green * (1f - alpha),
    blue = blue * alpha + ground.blue * (1f - alpha),
)

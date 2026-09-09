package com.wk2.climate.ui

import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.FakeVehicleBus
import com.wk2.climate.bus.Signal
import com.wk2.climate.design.Palette
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * The illumination rendering policy, end to end from a [ClimateState] to the
 * [Palette] the bar draws with.
 *
 * It lives in `:ui` rather than `:bus` because the two halves sit in different
 * modules on purpose — `ClimateState.isNight` reports what the vehicle said
 * (including `null` for "never said anything") and `Palette.forNight` owns
 * what to do about not knowing. `:bus` has no view of `:design`, so this is
 * the nearest place both are visible, and the pairing is what matters: a
 * future `?: false` anywhere between them fails here.
 *
 * The reason for the policy is asymmetric consequence, not symmetry. A bar
 * that is too dark is a nuisance the driver resolves by looking at it; a
 * full-brightness white bar at night in a moving vehicle is a hazard.
 */
class IlluminationPaletteTest {

    @Test
    fun `an unreported illumination renders night, not day`() {
        // The cold-start-at-night case: headlights already on, so illumination
        // never changes and the subscribe-only bus is never told a value.
        assertSame(Palette.NIGHT, Palette.forNight(ClimateState.EMPTY.isNight))
    }

    @Test
    fun `a reported zero renders day`() {
        val s = ClimateState.EMPTY.with(Signal.ILLUMINATION, 0)
        assertSame(Palette.DAY, Palette.forNight(s.isNight))
    }

    @Test
    fun `a reported one renders night`() {
        val s = ClimateState.EMPTY.with(Signal.ILLUMINATION, 1)
        assertSame(Palette.NIGHT, Palette.forNight(s.isNight))
    }

    @Test
    fun `the measured vehicle baseline reports day`() {
        // The baseline captured ILLUMINATION = 0 in daylight, so the fix must
        // not have turned every harness session into a night one.
        assertSame(Palette.DAY, Palette.forNight(FakeVehicleBus.VEHICLE_BASELINE.isNight))
    }
}

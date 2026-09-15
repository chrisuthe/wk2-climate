package com.wk2.climate.ui.bar

import com.wk2.climate.bus.SeatLevel

/**
 * What a seat button shows, derived once from the seat's heat and vent levels.
 *
 * One derivation rather than branches inline in the composable, so the four
 * states -- and the one the protocol says cannot happen -- are decided in a
 * place a unit test can reach. Heat and vent are mutually exclusive on the
 * vehicle, so both non-zero can only arrive transiently between two frames;
 * heat wins, arbitrarily but consistently.
 *
 * OFF is a positive claim and is only made when **both** levels are reported
 * as off. Anything short of that with nothing on is UNKNOWN and renders as an
 * em dash, matching every other readout in the bar: claiming a control is off
 * when we do not know is worse than admitting we do not know. `SeatLevel`
 * already carries UNAVAILABLE for an unreported signal, and
 * `hasClimateData == false` means both levels are UNAVAILABLE by construction,
 * so no second `live` gate is needed here.
 */
data class SeatIndicator(val kind: Kind, val litPips: Int) {

    enum class Kind { HEAT, COOL, OFF, UNKNOWN }

    companion object {
        fun of(heat: SeatLevel, vent: SeatLevel): SeatIndicator = when {
            heat.isOn -> SeatIndicator(Kind.HEAT, heat.litPips)
            vent.isOn -> SeatIndicator(Kind.COOL, vent.litPips)
            heat == SeatLevel.OFF && vent == SeatLevel.OFF -> SeatIndicator(Kind.OFF, 0)
            else -> SeatIndicator(Kind.UNKNOWN, 0)
        }
    }
}

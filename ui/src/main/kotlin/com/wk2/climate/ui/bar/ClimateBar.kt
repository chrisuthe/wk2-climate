package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette

/**
 * Screen 2a: the resting bar. 1080 x 227, and it never grows — 227dp is the
 * framework's `navigation_bar_height` and claiming more would cover app
 * content. Screen 1d opens *over* app content instead.
 *
 * Seven controls live here permanently: HOME, BACK, the two seat buttons,
 * AUTO, CLIMATE, and the two zones' steppers, plus volume. Everything else is
 * one tap away on 1d or in a seat's menu. Rendered entirely from [state];
 * every tap dispatches a [Command] and mutates nothing locally.
 *
 * [seatMenuOpen] is which seat's menu is up, so its button can paint itself
 * open; the menu itself is a separate window the service owns, and
 * [onSeatButton] is how a tap reaches it. Nothing about the menu is vehicle
 * state, so the bar only ever reads it.
 *
 * Until the vehicle has reported its first climate signal the climate half of
 * the bar renders *indeterminate* — see [ClimateState.hasClimateData]. Nothing
 * is filled, nothing that would read OFF says OFF, and the ink is muted. The
 * navigation column and the volume column do not depend on climate and stay
 * fully live: HOME and BACK are the only route a third-party app has to those
 * keys, and volume arrives on a different module entirely.
 */
@Composable
fun ClimateBar(
    state: ClimateState,
    onCommand: (Command) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    panelOpen: Boolean,
    onToggleClimate: () -> Unit,
    seatMenuOpen: SeatSide?,
    onSeatButton: (SeatSide) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Day and night differ in luminance only, never in layout, so muscle
    // memory holds. Driven by the vehicle's illumination signal, not a clock —
    // and `isNight` is null until the vehicle reports one, which
    // `Palette.forNight` renders as NIGHT on purpose. See it for why.
    val palette = Palette.forNight(state.isNight)

    // One gate, read straight off bus state. Absence is whole-module here —
    // CANBUS silent — so per-flag nullability would carry no more information.
    val live = state.hasClimateData

    Row(
        modifier
            .width(Dimens.barWidth)
            .height(Dimens.barHeight)
            .background(palette.surface),
    ) {
        BarNav(palette = palette, onHome = onHome, onBack = onBack)

        Column(Modifier.width(Dimens.barCenterColumn).height(Dimens.barHeight)) {
            BarTopRow(
                palette = palette,
                live = live,
                autoOn = state.autoOn,
                wheelOn = state.wheelHeatOn,
                panelOpen = panelOpen,
                driver = SeatIndicator.of(state.seatHeatL, state.seatVentL),
                passenger = SeatIndicator.of(state.seatHeatR, state.seatVentR),
                openMenu = seatMenuOpen,
                onAuto = { onCommand(Command.AUTO) },
                onSeatButton = onSeatButton,
                onClimate = onToggleClimate,
            )
            BarZones(
                palette = palette,
                live = live,
                driver = state.tempLeft,
                passenger = state.tempRight,
                onDriverDown = { onCommand(Command.TEMP_L_DOWN) },
                onDriverUp = { onCommand(Command.TEMP_L_UP) },
                onPassengerDown = { onCommand(Command.TEMP_R_DOWN) },
                onPassengerUp = { onCommand(Command.TEMP_R_UP) },
            )
        }

        BarVolume(
            palette = palette,
            volume = state.volume,
            onUp = { onCommand(Command.VOL_UP) },
            onDown = { onCommand(Command.VOL_DOWN) },
        )
    }
}

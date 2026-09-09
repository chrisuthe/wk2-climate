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
import com.wk2.climate.bus.SlotContent
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette

/**
 * Screen 2a: the resting bar. 1080 x 227, and it never grows — 227dp is the
 * framework's `navigation_bar_height` and claiming more would cover app
 * content. Screen 1d opens *over* app content instead.
 *
 * Six functions live here permanently, plus one adaptive slot. Everything else
 * is one tap away on 1d. Rendered entirely from [state]; every tap dispatches
 * a [Command] and mutates nothing locally.
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
    slot: SlotContent,
    onCommand: (Command) -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    onOpenClimate: () -> Unit,
    onSlotPressChange: (Boolean) -> Unit,
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
                wheelOn = state.wheelHeatOn,
                autoOn = state.autoOn,
                slot = slot,
                seatHeat = state.seatHeatL,
                seatVent = state.seatVentL,
                onWheel = { onCommand(Command.WHEEL_HEAT) },
                onSlot = {
                    onCommand(
                        when (slot) {
                            SlotContent.FRONT_DEFROST -> Command.FRONT_DEFROST
                            SlotContent.SEAT_HEAT -> Command.SEAT_HEAT_L
                            SlotContent.SEAT_COOL -> Command.SEAT_VENT_L
                        },
                    )
                },
                onAuto = { onCommand(Command.AUTO) },
                onClimate = onOpenClimate,
                onSlotPressChange = onSlotPressChange,
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

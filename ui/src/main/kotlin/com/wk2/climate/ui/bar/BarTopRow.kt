package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.Features
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.bus.SeatSlot
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.pressedTint
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The centre column's 96dp top row. On the WK2: driver seat button, AUTO,
 * CLIMATE, passenger seat button, at 128 / 256 / 256 / 128.
 *
 * Which cells exist, and how wide, comes from [topRowLayout]: a side with no
 * seat controls loses its seat cell and that half's main cell widens to 384,
 * so each half stays directly above its own temperature. For a given car
 * every cell lives at a constant coordinate and never changes what it holds,
 * so it can be found by feel. The driver half's main cell is AUTO, or A/C on
 * a car without AUTO; a driver side whose only control is the heated wheel
 * gets a [WheelButton] in place of the seat button.
 *
 * [live] is false until the vehicle has reported a climate signal. Every fill
 * in this row is a positive claim -- an amber AUTO says AUTO is engaged -- so
 * while [live] is false nothing here is filled and the ink is muted. The
 * controls stay tappable: a press is how the driver forces the vehicle to
 * report in the first place, and it was the owner's own workaround. The seat
 * buttons need no gate of their own: [SeatIndicator] is UNKNOWN by
 * construction while the seat levels are unreported.
 */
@Composable
fun BarTopRow(
    palette: Palette,
    live: Boolean,
    features: Features,
    autoOn: Boolean,
    acOn: Boolean,
    wheelOn: Boolean,
    panelOpen: Boolean,
    driver: SeatIndicator,
    passenger: SeatIndicator,
    openMenu: SeatSide?,
    onDriverMain: () -> Unit,
    onSeatButton: (SeatSide) -> Unit,
    onClimate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .width(Dimens.barCenterColumn)
            .height(Dimens.barTopRow),
    ) {
        topRowLayout(features).forEachIndexed { i, cell ->
            // Every cell but the leftmost draws the divider on its start edge.
            val divided = if (i > 0) Modifier.sideDivider(palette.divider, start = true) else Modifier
            when (cell.cell) {
                TopCell.DRIVER_SEAT ->
                    if (features.seatSlot(SeatSide.DRIVER) == SeatSlot.Direct(Command.WHEEL_HEAT)) {
                        WheelButton(
                            palette = palette,
                            live = live,
                            on = wheelOn,
                            onClick = { onSeatButton(SeatSide.DRIVER) },
                            modifier = divided,
                        )
                    } else {
                        // Carries the wheel badge when the wheel is one of its controls.
                        SeatButton(
                            palette = palette,
                            side = SeatSide.DRIVER,
                            indicator = driver,
                            wheelOn = live && wheelOn && features.has(Command.WHEEL_HEAT),
                            menuOpen = openMenu == SeatSide.DRIVER,
                            onClick = { onSeatButton(SeatSide.DRIVER) },
                            modifier = divided,
                        )
                    }

                TopCell.DRIVER_MAIN -> {
                    val auto = driverMainCommand(features) == Command.AUTO
                    MainCell(
                        palette = palette,
                        live = live,
                        label = if (auto) "AUTO" else "A/C",
                        on = if (auto) autoOn else acOn,
                        // A/C keeps its 1d colours when it stands in for AUTO.
                        fill = if (auto) palette.accent else palette.cool,
                        fillInk = if (auto) palette.accentInk else palette.surface,
                        width = cell.width,
                        onClick = onDriverMain,
                        modifier = divided,
                    )
                }

                TopCell.CLIMATE -> ClimateCell(palette, panelOpen, cell.width, onClimate, divided)

                // No wheel badge on the passenger side.
                TopCell.PASSENGER_SEAT -> SeatButton(
                    palette = palette,
                    side = SeatSide.PASSENGER,
                    indicator = passenger,
                    wheelOn = false,
                    menuOpen = openMenu == SeatSide.PASSENGER,
                    onClick = { onSeatButton(SeatSide.PASSENGER) },
                    modifier = divided,
                )
            }
        }
    }
}

/** AUTO, or A/C standing in for it: a filled cell while on. */
@Composable
private fun MainCell(
    palette: Palette,
    live: Boolean,
    label: String,
    on: Boolean,
    fill: Color,
    fillInk: Color,
    width: Dp,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val (interaction, pressed) = rememberPressState()
    Row(
        modifier
            .width(width)
            .fillMaxHeight()
            // `on` is already false while indeterminate -- flag() reads an
            // absent signal as false -- but that is the bug, not the guard.
            // Gating the fill on `live` makes the colour a claim we only ever
            // paint from data we actually have.
            .background(if (live && on) fill else Color.Transparent)
            .then(if (pressed.value) Modifier.pressedTint(live && on, palette) else Modifier)
            .target(interaction, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = Type.barAuto.copy(
                color = when {
                    !live -> palette.inkMuted
                    on -> fillInk
                    else -> palette.ink
                },
            ),
        )
    }
}

@Composable
private fun ClimateCell(palette: Palette, panelOpen: Boolean, width: Dp, onClick: () -> Unit, modifier: Modifier) {
    val (interaction, pressed) = rememberPressState()
    Row(
        modifier
            .width(width)
            .fillMaxHeight()
            .background(if (pressed.value) palette.surfaceRaised else Color.Transparent)
            .target(interaction, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "CLIMATE",
            style = Type.barControlLabel.copy(color = palette.ink),
        )
        Spacer(Modifier.width(10.dp))
        BasicText(
            // Points the way the tap will move the panel: up to open,
            // down to dismiss. The same control does both.
            text = if (panelOpen) "▼" else "▲",
            style = Type.barCaret.copy(color = palette.ink.copy(alpha = 0.55f)),
        )
    }
}

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
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.pressedTint
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The centre column's 96dp top row: driver seat button, AUTO, CLIMATE,
 * passenger seat button.
 *
 * Widths are 128 / 256 / 256 / 128 and are **fixed** -- see [Dimens.seatButton]
 * and its neighbours, which are the single source of truth for them. Every
 * cell lives at a constant coordinate and never changes what it holds, so it
 * can be found by feel; the row is mirror-symmetric about the centre divider,
 * matching the zones beneath it. The two adaptive cells that used to sit
 * between AUTO and CLIMATE, and the outside-temperature machine that swapped
 * them, are gone -- what they surfaced now lives in each seat's menu.
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
    autoOn: Boolean,
    wheelOn: Boolean,
    panelOpen: Boolean,
    driver: SeatIndicator,
    passenger: SeatIndicator,
    openMenu: SeatSide?,
    onAuto: () -> Unit,
    onSeatButton: (SeatSide) -> Unit,
    onClimate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .width(Dimens.barCenterColumn)
            .height(Dimens.barTopRow),
    ) {
        // ---- driver seat, 128dp. Leftmost. Carries the wheel badge. ----
        SeatButton(
            palette = palette,
            side = SeatSide.DRIVER,
            indicator = driver,
            wheelOn = live && wheelOn,
            menuOpen = openMenu == SeatSide.DRIVER,
            onClick = { onSeatButton(SeatSide.DRIVER) },
        )

        // ---- AUTO, 256dp ----
        val (autoInteraction, autoPressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.slotAuto)
                .fillMaxHeight()
                .sideDivider(palette.divider, start = true)
                // `autoOn` is already false while indeterminate -- flag() reads
                // an absent signal as false -- but that is the bug, not the
                // guard. Gating the fill on `live` makes the amber a claim we
                // only ever paint from data we actually have.
                .background(if (live && autoOn) palette.accent else Color.Transparent)
                .then(
                    if (autoPressed.value) {
                        Modifier.pressedTint(live && autoOn, palette)
                    } else {
                        Modifier
                    },
                )
                .target(autoInteraction, onClick = onAuto),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "AUTO",
                style = Type.barAuto.copy(
                    color = when {
                        !live -> palette.inkMuted
                        autoOn -> palette.accentInk
                        else -> palette.ink
                    },
                ),
            )
        }

        // ---- CLIMATE, 256dp ----
        val (climateInteraction, climatePressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.slotClimate)
                .fillMaxHeight()
                .sideDivider(palette.divider, start = true)
                .background(
                    if (climatePressed.value) palette.surfaceRaised else Color.Transparent,
                )
                .target(climateInteraction, onClick = onClimate),
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

        // ---- passenger seat, 128dp. Rightmost. No wheel badge. ----
        SeatButton(
            palette = palette,
            side = SeatSide.PASSENGER,
            indicator = passenger,
            wheelOn = false,
            menuOpen = openMenu == SeatSide.PASSENGER,
            onClick = { onSeatButton(SeatSide.PASSENGER) },
            modifier = Modifier.sideDivider(palette.divider, start = true),
        )
    }
}

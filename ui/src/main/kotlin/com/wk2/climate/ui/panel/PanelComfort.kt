package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.SeatLevel
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.pressedTint
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * Seat heat, seat ventilation, and the heated wheel.
 *
 * [live] is false until the vehicle has reported a climate signal. Only the
 * wheel needs it as a *parameter*: its flag is `raw[signal] == 1` and
 * collapses absent into false, so it is gated and both lights nothing and
 * drops its warm resting wash, ring and label to neutral ink -- a warm tile
 * with an orange ring is precisely the resting look of a wheel we know to be
 * off, which is what made the indeterminate page indistinguishable from a
 * working one.
 *
 * The seat tiles need no gate: [SeatLevel] carries UNAVAILABLE for an
 * unreported signal, and `hasClimateData == false` means the level *is*
 * UNAVAILABLE by construction. They mute off the level itself for that reason
 * -- one honest source, no second gate -- matching `BarTopRow`'s SeatSlot.
 */
@Composable
fun PanelComfort(
    palette: Palette,
    state: ClimateState,
    live: Boolean,
    onCommand: (Command) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ComfortTile(
                palette, "SEAT HEAT \u00B7 L", state.seatHeatL, palette.warm,
                { onCommand(Command.SEAT_HEAT_L) }, Modifier.weight(1f),
            )
            ComfortTile(
                palette, "SEAT HEAT \u00B7 R", state.seatHeatR, palette.warm,
                { onCommand(Command.SEAT_HEAT_R) }, Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ComfortTile(
                palette, "SEAT COOL \u00B7 L", state.seatVentL, palette.cool,
                { onCommand(Command.SEAT_VENT_L) }, Modifier.weight(1f),
            )
            ComfortTile(
                palette, "SEAT COOL \u00B7 R", state.seatVentR, palette.cool,
                { onCommand(Command.SEAT_VENT_R) }, Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(14.dp))
        HeatedWheel(palette, live = live, on = state.wheelHeatOn) { onCommand(Command.WHEEL_HEAT) }
    }
}

/**
 * One seat control.
 *
 * Two pips carry three states: none lit is OFF, one is LOW, both are HIGH.
 * That is not a compromise — the vehicle's own cycle is `0 -> 3 -> 1 -> 0` and
 * never visits 2, so every tap changes what the driver sees.
 *
 * An UNAVAILABLE level shows a dash and no lit pips rather than OFF: claiming
 * a control is off when we do not know is worse than admitting we do not.
 */
@Composable
private fun ComfortTile(
    palette: Palette,
    title: String,
    level: SeatLevel,
    litColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (interaction, pressed) = rememberPressState()
    val shape = RoundedCornerShape(Dimens.radiusTile)
    val on = level.isOn
    // An UNAVAILABLE level already renders a dash, but full-strength ink makes
    // that dash read as a considered value. Muting the title as well is what
    // `BarTopRow`'s SeatSlot does, and it is driven by the level rather than by
    // a separate `live` flag because the level is already the honest reading.
    val known = level != SeatLevel.UNAVAILABLE

    Column(
        modifier
            .height(Dimens.comfortTileHeight)
            .background(
                if (pressed.value) palette.ink.copy(alpha = 0.08f) else palette.surfaceRaised,
                shape,
            )
            .border(
                Dimens.controlBorderWidth,
                if (on) litColor.copy(alpha = 0.5f) else palette.ink.copy(alpha = 0.14f),
                shape,
            )
            .target(interaction, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            text = title,
            style = Type.comfortTitle.copy(color = if (known) palette.ink else palette.inkMuted),
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(2) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(14.dp)
                        .background(
                            if (index < level.litPips) litColor else palette.ink.copy(alpha = 0.13f),
                            RoundedCornerShape(7.dp),
                        ),
                )
                // 8dp between the two pips, per the handoff mockup's
                // `display:flex;gap:8px` pip group (System Navigation.dc.html
                // lines 438/442/446/450). Not the fan meter's `gap: 6px`
                // (README:180) -- that is a different control.
                if (index == 0) Spacer(Modifier.width(8.dp))
            }
            Spacer(Modifier.width(14.dp))
            BasicText(
                text = when (level) {
                    SeatLevel.OFF -> "OFF"
                    SeatLevel.LOW -> "LOW"
                    SeatLevel.HIGH -> "HIGH"
                    SeatLevel.UNAVAILABLE -> "\u2014"
                },
                style = Type.comfortState.copy(
                    color = if (on) litColor else palette.inkFaint,
                ),
            )
        }
    }
}

/**
 * The heated wheel.
 *
 * Every warm thing in here is a claim: the tinted wash, the 0.5-alpha ring and
 * the solid glyph all say "this control is a heater, and here is where it
 * sits". While [live] is false none of them is painted -- the tile falls back
 * to the page's neutral outlined-control treatment and muted ink, so it reads
 * as indeterminate rather than as a wheel we have been told is off. The
 * pressed wash stays, from the same `pressedTint` every other outlined control
 * uses: a press is how the driver forces the vehicle to report.
 */
@Composable
private fun HeatedWheel(palette: Palette, live: Boolean, on: Boolean, onClick: () -> Unit) {
    val (interaction, pressed) = rememberPressState()
    val shape = RoundedCornerShape(Dimens.radiusTile)
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.comfortTileHeight)
            .then(
                if (live) {
                    Modifier.background(
                        palette.warm.copy(
                            alpha = if (pressed.value) 0.24f else if (on) 0.12f else 0.04f,
                        ),
                        shape,
                    )
                } else if (pressed.value) {
                    Modifier.pressedTint(filled = false, palette = palette, shape = shape)
                } else {
                    Modifier
                },
            )
            .border(
                Dimens.controlBorderWidth,
                when {
                    !live -> palette.borderControl
                    on -> palette.warm.copy(alpha = 0.5f)
                    else -> palette.warm.copy(alpha = 0.2f)
                },
                shape,
            )
            .target(interaction, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .then(
                    when {
                        // Faint ink, not warm: a warm ring is the resting look
                        // of a control we know to be off. Same choice, same
                        // token, as the bar's 19dp wheel glyph.
                        !live -> Modifier.border(3.dp, palette.inkFaint, CircleShape)
                        on -> Modifier.background(palette.warmBright, CircleShape)
                        else -> Modifier.border(3.dp, palette.warmBright, CircleShape)
                    },
                ),
        )
        Spacer(Modifier.width(14.dp))
        BasicText(
            text = "HEATED STEERING WHEEL",
            style = Type.wheelLabel.copy(color = if (live) palette.ink else palette.inkMuted),
        )
    }
}

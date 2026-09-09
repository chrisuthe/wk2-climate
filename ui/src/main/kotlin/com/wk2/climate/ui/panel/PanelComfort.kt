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
import com.wk2.climate.ui.SeatHeatLeftGlyph
import com.wk2.climate.ui.SeatHeatRightGlyph
import com.wk2.climate.ui.SeatCoolRightGlyph
import com.wk2.climate.ui.SeatCoolLeftGlyph
import com.wk2.climate.ui.WheelHeatGlyph
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.tileGap)) {
            ComfortTile(
                palette, state.seatHeatL, palette.warm,
                glyph = { tint -> SeatHeatLeftGlyph(tint, SEAT_GLYPH) },
                onClick = { onCommand(Command.SEAT_HEAT_L) },
                modifier = Modifier.weight(1f),
            )
            ComfortTile(
                palette, state.seatHeatR, palette.warm,
                glyph = { tint -> SeatHeatRightGlyph(tint, SEAT_GLYPH) },
                onClick = { onCommand(Command.SEAT_HEAT_R) },
                modifier = Modifier.weight(1f),
                mirrored = true,
            )
        }
        Spacer(Modifier.height(Dimens.tileGap))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.tileGap)) {
            ComfortTile(
                palette, state.seatVentL, palette.cool,
                glyph = { tint -> SeatCoolLeftGlyph(tint, SEAT_GLYPH) },
                onClick = { onCommand(Command.SEAT_VENT_L) },
                modifier = Modifier.weight(1f),
            )
            ComfortTile(
                palette, state.seatVentR, palette.cool,
                glyph = { tint -> SeatCoolRightGlyph(tint, SEAT_GLYPH) },
                onClick = { onCommand(Command.SEAT_VENT_R) },
                modifier = Modifier.weight(1f),
                mirrored = true,
            )
        }

        Spacer(Modifier.height(Dimens.tileGap))
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
    level: SeatLevel,
    litColor: Color,
    glyph: @Composable (Color) -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mirrored: Boolean = false,
) {
    val (interaction, pressed) = rememberPressState()
    val shape = RoundedCornerShape(Dimens.radiusTile)
    val on = level.isOn
    // Drives the glyph's tint as well as the readout. Taken from the level
    // rather than from a separate `live` flag, because the level is already the
    // honest reading -- `hasClimateData == false` means UNAVAILABLE by
    // construction.
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
        // One centred row. The glyph says which seat and what it does, so the
        // tile carries no words: there is nothing to push apart.
        verticalArrangement = Arrangement.Center,
    ) {
        // The glyph sits **outboard**: left on the driver's tiles, right on the
        // passenger's, so each row mirrors about the centre gap and matches
        // where the seats actually are. Which seat a tile controls is then
        // legible from the layout, before the glyph itself is read.
        val tint = when {
            !known -> palette.inkFaint
            on -> litColor
            else -> palette.ink
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mirrored) {
                SeatState(palette, level, litColor)
                Spacer(Modifier.width(14.dp))
                SeatPips(palette, level, litColor, Modifier.weight(1f))
                Spacer(Modifier.width(16.dp))
                glyph(tint)
            } else {
                glyph(tint)
                Spacer(Modifier.width(16.dp))
                SeatPips(palette, level, litColor, Modifier.weight(1f))
                Spacer(Modifier.width(14.dp))
                SeatState(palette, level, litColor)
            }
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
        WheelHeatGlyph(
            // Tint-only states, same reasoning as the bar's glyph: warm only
            // when it is heating, neutral ink when we know it is off, faint
            // when we have been told nothing. See WheelHeatGlyph.
            tint = when {
                !live -> palette.inkFaint
                on -> palette.warmBright
                else -> palette.ink
            },
            size = 30.dp,
        )
        Spacer(Modifier.width(14.dp))
        BasicText(
            text = "HEATED STEERING WHEEL",
            style = Type.wheelLabel.copy(color = if (live) palette.ink else palette.inkMuted),
        )
    }
}

/**
 * The seat glyphs' size.
 *
 * One value for all four tiles: they are a set, and the left/right pair on each
 * row must be identical or the asymmetry reads as a bug.
 *
 * Grown twice from the 30dp first tried, which was legible but did not carry a
 * 96dp tile. At 48 the glyph is the tile, and the pips gave up 4dp of height to
 * make room -- 10dp rather than the handoff's 14.
 */
private val SEAT_GLYPH = 48.dp

/**
 * The two-pip level indicator.
 *
 * Slimmer than the handoff's 14dp: the glyph is the tile's subject now and the
 * pips are the qualifier, so they read better as a lighter weight beside it.
 * Extracted so the driver and passenger orderings share one definition rather
 * than mirroring a copy.
 */
@Composable
private fun SeatPips(
    palette: Palette,
    level: SeatLevel,
    litColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(2) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(10.dp)
                    .background(
                        if (index < level.litPips) litColor else palette.ink.copy(alpha = 0.13f),
                        RoundedCornerShape(5.dp),
                    ),
            )
            // 8dp between the two pips, per the handoff mockup's
            // `display:flex;gap:8px` pip group (System Navigation.dc.html lines
            // 438/442/446/450). Not the fan meter's `gap: 6px` (README:180) --
            // that is a different control.
            if (index == 0) Spacer(Modifier.width(8.dp))
        }
    }
}

/** The word beside the pips. A dash for UNAVAILABLE, never `OFF`. */
@Composable
private fun SeatState(palette: Palette, level: SeatLevel, litColor: Color) {
    BasicText(
        text = when (level) {
            SeatLevel.OFF -> "OFF"
            SeatLevel.LOW -> "LOW"
            SeatLevel.HIGH -> "HIGH"
            SeatLevel.UNAVAILABLE -> "\u2014"
        },
        style = Type.comfortState.copy(
            color = if (level.isOn) litColor else palette.inkFaint,
        ),
    )
}

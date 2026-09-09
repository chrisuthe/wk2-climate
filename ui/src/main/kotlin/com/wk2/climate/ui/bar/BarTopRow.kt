package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.SeatLevel
import com.wk2.climate.bus.SlotBand
import com.wk2.climate.bus.SlotCell
import com.wk2.climate.bus.SlotContent
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.Glyph
import com.wk2.climate.ui.FrontDefrostGlyph
import com.wk2.climate.ui.GlyphIcon
import com.wk2.climate.ui.WheelHeatGlyph
import com.wk2.climate.ui.pressedTint
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The centre column's 96dp top row: AUTO, the two adaptive cells, and the
 * CLIMATE opener.
 *
 * Widths are 210 / 200 / 174 / 184 and are **fixed** -- see [Dimens.slotAuto]
 * and its neighbours, which are the single source of truth for them. AUTO,
 * CLIMATE and both of the bar's side columns live at constant coordinates and
 * never change what they hold, so they can be found by feel. The **two middle
 * cells are the adaptive region**: they hold a [SlotBand]'s pair, and they
 * change together or not at all. Nothing else in the bar moves, resizes or
 * changes position when they do.
 *
 * That the middle pair varies at all is a deliberate override of the
 * find-by-feel rule, made by the owner having been shown the consequence.
 * [com.wk2.climate.bus.AdaptiveSlot] is what keeps the cost bounded: hysteresis,
 * a 30s dwell, and a hard rule that nothing changes under a finger or just
 * after a tap.
 *
 * [live] is false until the vehicle has reported a climate signal. Every fill
 * in this row is a positive claim -- an amber AUTO says AUTO is engaged -- so
 * while [live] is false nothing here is filled and the ink is muted. The
 * controls stay tappable: a press is how the driver forces the vehicle to
 * report in the first place, and it was the owner's own workaround.
 */
@Composable
fun BarTopRow(
    palette: Palette,
    live: Boolean,
    band: SlotBand,
    autoOn: Boolean,
    wheelOn: Boolean,
    frontDefrostOn: Boolean,
    maxAcOn: Boolean,
    panelOpen: Boolean,
    seatHeat: SeatLevel,
    seatVent: SeatLevel,
    onAuto: () -> Unit,
    onSlot: (SlotContent) -> Unit,
    onClimate: () -> Unit,
    onSlotPressChange: (SlotCell, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .width(Dimens.barCenterColumn)
            .height(Dimens.barTopRow),
    ) {
        // ---- AUTO, 210dp. Leftmost, and the widest cell in the row. ----
        val (autoInteraction, autoPressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.slotAuto)
                .fillMaxHeight()
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

        // ---- the adaptive pair, 200dp then 174dp ----
        AdaptiveSlotCell(
            palette = palette,
            live = live,
            cell = SlotCell.FIRST,
            width = Dimens.slotAdaptiveFirst,
            content = band.first,
            wheelOn = wheelOn,
            frontDefrostOn = frontDefrostOn,
            maxAcOn = maxAcOn,
            seatHeat = seatHeat,
            seatVent = seatVent,
            onSlot = onSlot,
            onPressChange = onSlotPressChange,
        )
        AdaptiveSlotCell(
            palette = palette,
            live = live,
            cell = SlotCell.SECOND,
            width = Dimens.slotAdaptiveSecond,
            content = band.second,
            wheelOn = wheelOn,
            frontDefrostOn = frontDefrostOn,
            maxAcOn = maxAcOn,
            seatHeat = seatHeat,
            seatVent = seatVent,
            onSlot = onSlot,
            onPressChange = onSlotPressChange,
        )

        // ---- CLIMATE, 184dp ----
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
                text = if (panelOpen) "\u25BC" else "\u25B2",
                style = Type.barCaret.copy(color = palette.ink.copy(alpha = 0.55f)),
            )
        }
    }
}

/**
 * One of the two variable cells. 200 x 96 and 174 x 96, and **nothing else in
 * the bar moves, resizes or changes position** when their contents change.
 *
 * Whatever they hold also exists on screen 1d -- these are shortcuts, never the
 * only route to a function.
 *
 * The cell's fill is read from bus state ([frontDefrostOn], [maxAcOn]) and
 * never from which [content] the band selected. Which control is *offered*
 * here is a function of the outside temperature; whether it is *engaged* is a
 * claim about the vehicle, and only the vehicle gets to make it.
 */
@Composable
private fun AdaptiveSlotCell(
    palette: Palette,
    live: Boolean,
    cell: SlotCell,
    width: Dp,
    content: SlotContent,
    wheelOn: Boolean,
    frontDefrostOn: Boolean,
    maxAcOn: Boolean,
    seatHeat: SeatLevel,
    seatVent: SeatLevel,
    onSlot: (SlotContent) -> Unit,
    onPressChange: (SlotCell, Boolean) -> Unit,
) {
    val (interaction, pressed) = rememberPressState()

    // Neither cell may change contents while a finger is on *either* of them,
    // so the state machine needs to know about the press -- and which cell it
    // was, or this cell's release would clear the other cell's freeze.
    LaunchedEffect(pressed.value) { onPressChange(cell, pressed.value) }

    val filled = live && when (content) {
        SlotContent.FRONT_DEFROST -> frontDefrostOn
        SlotContent.MAX_AC -> maxAcOn
        // WHEEL carries its state in the glyph's tint and the seat cells in
        // their level word, exactly as they do on the rest of the bar.
        SlotContent.WHEEL, SlotContent.SEAT_HEAT, SlotContent.SEAT_COOL -> false
    }
    val isCool = content == SlotContent.MAX_AC
    val fillColor = if (isCool) palette.cool else palette.accent
    val ink = when {
        !live -> palette.inkMuted
        filled -> if (isCool) palette.surface else palette.accentInk
        else -> palette.ink
    }

    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .sideDivider(palette.divider, start = true)
            .then(
                if (filled) {
                    // A filled cell insets its fill on the side that touches
                    // AUTO, leaving 4dp of surface, so an amber FRONT DEFROST
                    // beside an amber AUTO does not read as one wide tile.
                    // Only the first cell touches AUTO.
                    Modifier
                        .padding(start = if (cell == SlotCell.FIRST) 4.dp else 0.dp)
                        .background(fillColor)
                } else {
                    Modifier
                },
            )
            .then(if (pressed.value) Modifier.pressedTint(filled, palette) else Modifier)
            .target(interaction, onClick = { onSlot(content) }),
        contentAlignment = Alignment.Center,
    ) {
        when (content) {
            SlotContent.FRONT_DEFROST -> Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FrontDefrostGlyph(tint = ink, size = 34.dp)
                Spacer(Modifier.width(14.dp))
                BasicText(
                    text = "FRONT DEFROST",
                    style = Type.slotLabelSmall.copy(color = ink),
                )
            }

            SlotContent.WHEEL -> Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // A filled glyph, so the three states are the tint alone -- see
                // WheelHeatGlyph. Warm only when it is actually heating; neutral
                // ink when we know it is off; faint when we have been told
                // nothing.
                WheelHeatGlyph(
                    tint = when {
                        !live -> palette.inkFaint
                        wheelOn -> palette.warm
                        else -> palette.ink
                    },
                    size = 24.dp,
                )
                Spacer(Modifier.width(9.dp))
                BasicText(
                    text = "WHEEL",
                    style = Type.barControlLabel.copy(
                        color = if (live) palette.ink.copy(alpha = 0.85f) else palette.inkMuted,
                    ),
                )
            }

            // A plain toggle with no confirmation, by the owner's choice, and
            // lit only from `state.maxAcOn`. Measured on this vehicle the macro
            // forces recirculation, drives the fan to 7, clears AUTO and sends
            // both setpoints to the LO sentinel -- so what the tile says has to
            // be what the vehicle reports back, not what we assumed the tap did.
            SlotContent.MAX_AC -> BasicText(
                text = "MAX A/C",
                style = Type.slotLabel.copy(color = ink),
            )

            // An indeterminate level, not OFF. SeatSlot already renders
            // UNAVAILABLE as an em dash, matching Temp.Unavailable in the
            // zones, so there is one spelling of "we do not know" in the bar.
            SlotContent.SEAT_HEAT -> SeatSlot(
                palette = palette,
                title = "SEAT HEAT",
                level = if (live) seatHeat else SeatLevel.UNAVAILABLE,
                labelColor = if (live) palette.ink.copy(alpha = 0.85f) else palette.inkMuted,
            )

            SlotContent.SEAT_COOL -> SeatSlot(
                palette = palette,
                title = "SEAT COOL",
                level = if (live) seatVent else SeatLevel.UNAVAILABLE,
                labelColor = if (live) palette.coolLabel else palette.inkMuted,
            )
        }
    }
}

@Composable
private fun SeatSlot(
    palette: Palette,
    title: String,
    level: SeatLevel,
    labelColor: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BasicText(text = title, style = Type.slotLabel.copy(color = labelColor))
        Spacer(Modifier.height(5.dp))
        BasicText(
            // OFF / LOW / HIGH. UNAVAILABLE renders as a dash rather than as
            // OFF, because claiming a control is off when we do not know is
            // worse than admitting we do not know.
            text = when (level) {
                SeatLevel.OFF -> "OFF"
                SeatLevel.LOW -> "LOW"
                SeatLevel.HIGH -> "HIGH"
                SeatLevel.UNAVAILABLE -> "\u2014"
            },
            style = Type.slotState.copy(color = palette.ink.copy(alpha = 0.4f)),
        )
    }
}

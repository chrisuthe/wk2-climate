package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.SeatLevel
import com.wk2.climate.bus.SlotContent
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.Glyph
import com.wk2.climate.ui.GlyphIcon
import com.wk2.climate.ui.pressedTint
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The centre column's 96dp top row: heated wheel, the adaptive slot, AUTO,
 * and the CLIMATE opener.
 *
 * Widths are 210 / 200 / 174 / 184 and are **fixed**. Controls live at constant
 * coordinates so they can be found by feel, and the adaptive slot is the only
 * region in the whole bar whose contents ever change.
 *
 * [live] is false until the vehicle has reported a climate signal. Every fill
 * in this row is a positive claim — an amber AUTO says AUTO is engaged — so
 * while [live] is false nothing here is filled and the ink is muted. The
 * controls stay tappable: a press is how the driver forces the vehicle to
 * report in the first place, and it was the owner's own workaround.
 */
@Composable
fun BarTopRow(
    palette: Palette,
    live: Boolean,
    wheelOn: Boolean,
    autoOn: Boolean,
    slot: SlotContent,
    panelOpen: Boolean,
    seatHeat: SeatLevel,
    seatVent: SeatLevel,
    onWheel: () -> Unit,
    onSlot: () -> Unit,
    onAuto: () -> Unit,
    onClimate: () -> Unit,
    onSlotPressChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .width(Dimens.barCenterColumn)
            .height(Dimens.barTopRow),
    ) {
        // ---- heated wheel, 210dp ----
        val (wheelInteraction, wheelPressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.slotWheel)
                .fillMaxHeight()
                .sideDivider(palette.divider, start = false)
                .background(
                    if (wheelPressed.value) palette.surfaceRaised else Color.Transparent,
                )
                .target(wheelInteraction, onClick = onWheel),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(19.dp)
                    .then(
                        when {
                            // Never filled while indeterminate, and the ring
                            // drops to faint ink rather than staying warm: a
                            // warm ring is the resting look of a control we
                            // know to be off.
                            !live -> Modifier.border(2.5.dp, palette.inkFaint, CircleShape)
                            wheelOn -> Modifier.background(palette.warm, CircleShape)
                            else -> Modifier.border(2.5.dp, palette.warm, CircleShape)
                        },
                    ),
            )
            Spacer(Modifier.width(9.dp))
            BasicText(
                text = "WHEEL",
                style = Type.barControlLabel.copy(
                    color = if (live) palette.ink.copy(alpha = 0.85f) else palette.inkMuted,
                ),
            )
        }

        // ---- the adaptive slot, 200dp ----
        AdaptiveSlotCell(
            palette = palette,
            live = live,
            slot = slot,
            seatHeat = seatHeat,
            seatVent = seatVent,
            onClick = onSlot,
            onPressChange = onSlotPressChange,
        )

        // ---- AUTO, 174dp ----
        val (autoInteraction, autoPressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.slotAuto)
                .fillMaxHeight()
                // `autoOn` is already false while indeterminate — flag() reads
                // an absent signal as false — but that is the bug, not the
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
 * The one variable region. 200 x 96, and **nothing else in the bar moves,
 * resizes or changes position** when its contents change.
 *
 * Whatever it holds also exists on screen 1d — it is a shortcut, never the
 * only route to a function.
 */
@Composable
private fun AdaptiveSlotCell(
    palette: Palette,
    live: Boolean,
    slot: SlotContent,
    seatHeat: SeatLevel,
    seatVent: SeatLevel,
    onClick: () -> Unit,
    onPressChange: (Boolean) -> Unit,
) {
    val (interaction, pressed) = rememberPressState()

    // The slot must not change contents while a finger is on it, so the state
    // machine needs to know about the press.
    LaunchedEffect(pressed.value) { onPressChange(pressed.value) }

    // FRONT DEFROST is chosen from the *outside* temperature, which arrives on
    // MAIN and can be present while CANBUS is silent. Its amber fill would
    // otherwise be the one tile that lights up with no climate data behind it.
    val isDefrost = live && slot == SlotContent.FRONT_DEFROST

    Box(
        Modifier
            .width(Dimens.slotAdaptive)
            .fillMaxHeight()
            .then(
                if (isDefrost) {
                    // Amber fill, with a 4dp surface-coloured right border to
                    // separate it from the adjacent amber AUTO.
                    Modifier
                        .padding(end = 4.dp)
                        .background(palette.accent)
                } else {
                    Modifier.sideDivider(palette.divider, start = false)
                },
            )
            .then(if (pressed.value) Modifier.pressedTint(isDefrost, palette) else Modifier)
            .target(interaction, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (slot) {
            SlotContent.FRONT_DEFROST -> Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val defrostInk = if (live) palette.accentInk else palette.inkMuted
                GlyphIcon(Glyph.defrost, tint = defrostInk, height = 34.dp)
                Spacer(Modifier.width(14.dp))
                BasicText(
                    text = "FRONT DEFROST",
                    style = Type.slotLabelSmall.copy(color = defrostInk),
                )
            }

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


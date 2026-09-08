package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The centre column's 96dp top row: heated wheel, the adaptive slot, AUTO,
 * and the CLIMATE opener.
 *
 * Widths are 210 / 200 / 174 / 184 and are **fixed**. Controls live at constant
 * coordinates so they can be found by feel, and the adaptive slot is the only
 * region in the whole bar whose contents ever change.
 */
@Composable
fun BarTopRow(
    palette: Palette,
    wheelOn: Boolean,
    autoOn: Boolean,
    slot: SlotContent,
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
                        if (wheelOn) {
                            Modifier.background(palette.warm, CircleShape)
                        } else {
                            Modifier.border(2.5.dp, palette.warm, CircleShape)
                        },
                    ),
            )
            Spacer(Modifier.width(9.dp))
            BasicText(
                text = "WHEEL",
                style = Type.barControlLabel.copy(color = palette.ink.copy(alpha = 0.85f)),
            )
        }

        // ---- the adaptive slot, 200dp ----
        AdaptiveSlotCell(
            palette = palette,
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
                .background(if (autoOn) palette.accent else Color.Transparent)
                .then(if (autoPressed.value) Modifier.pressedTint(autoOn, palette) else Modifier)
                .target(autoInteraction, onClick = onAuto),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "AUTO",
                style = Type.barAuto.copy(
                    color = if (autoOn) palette.accentInk else palette.ink,
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
                text = "\u25B2",
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

    val isDefrost = slot == SlotContent.FRONT_DEFROST

    Box(
        Modifier
            .width(Dimens.slotAdaptive)
            .fillMaxHeight()
            .then(
                if (isDefrost) {
                    // Amber fill, with a 4dp surface-coloured right border to
                    // separate it from the adjacent amber AUTO.
                    Modifier
                        .background(palette.accent)
                        .padding(end = 4.dp)
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
                GlyphIcon(Glyph.defrost, tint = palette.accentInk, height = 34.dp)
                Spacer(Modifier.width(14.dp))
                BasicText(
                    text = "FRONT DEFROST",
                    style = Type.slotLabelSmall.copy(color = palette.accentInk),
                )
            }

            SlotContent.SEAT_HEAT -> SeatSlot(
                palette = palette,
                title = "SEAT HEAT",
                level = seatHeat,
                labelColor = palette.ink.copy(alpha = 0.85f),
            )

            SlotContent.SEAT_COOL -> SeatSlot(
                palette = palette,
                title = "SEAT COOL",
                level = seatVent,
                labelColor = palette.coolLabel,
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

/**
 * The pressed treatment: brightness on a filled target, a light wash on an
 * outlined one. Applied on touch-down.
 */
private fun Modifier.pressedTint(filled: Boolean, palette: Palette): Modifier =
    if (filled) {
        // brightness(1.25) equivalent — overlay white at low alpha.
        background(Color.White.copy(alpha = 0.2f))
    } else {
        background(palette.surfaceRaised)
    }

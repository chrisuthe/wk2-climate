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
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

@Composable
fun PanelComfort(
    palette: Palette,
    state: ClimateState,
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
        HeatedWheel(palette, state.wheelHeatOn) { onCommand(Command.WHEEL_HEAT) }
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
        BasicText(text = title, style = Type.comfortTitle.copy(color = palette.ink))

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
                if (index == 0) Spacer(Modifier.width(6.dp))
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

@Composable
private fun HeatedWheel(palette: Palette, on: Boolean, onClick: () -> Unit) {
    val (interaction, pressed) = rememberPressState()
    val shape = RoundedCornerShape(Dimens.radiusTile)
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.comfortTileHeight)
            .background(
                palette.warm.copy(alpha = if (pressed.value) 0.24f else if (on) 0.12f else 0.04f),
                shape,
            )
            .border(Dimens.controlBorderWidth, palette.warm.copy(alpha = if (on) 0.5f else 0.2f), shape)
            .target(interaction, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .then(
                    if (on) {
                        Modifier.background(palette.warmBright, CircleShape)
                    } else {
                        Modifier.border(3.dp, palette.warmBright, CircleShape)
                    },
                ),
        )
        Spacer(Modifier.width(14.dp))
        BasicText(
            text = "HEATED STEERING WHEEL",
            style = Type.wheelLabel.copy(color = palette.ink),
        )
    }
}

package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.WheelHeatGlyph
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The driver slot on a car with a heated wheel and no seat controls: a
 * straight wheel on/off button in the same 128 x 96 cell a seat button
 * would hold.
 *
 * It follows the seat button's grammar so the bar reads as one system: the
 * glyph in the lit colour when on, `ink` when off, `inkFaint` when unknown;
 * `ON` in the lit colour, `OFF` muted, an em dash when the vehicle has not
 * reported. [live] gates the fill for the same reason it does everywhere in
 * the bar -- the wheel flag collapses "absent" into false.
 */
@Composable
fun WheelButton(
    palette: Palette,
    live: Boolean,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (interaction, pressed) = rememberPressState()
    val lit = live && on
    Box(
        modifier
            .width(Dimens.seatButton)
            .fillMaxHeight()
            .background(if (pressed.value) palette.surfaceRaised else Color.Transparent)
            .target(interaction, onClick = onClick),
    ) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            WheelHeatGlyph(
                tint = when {
                    !live -> palette.inkFaint
                    on -> palette.warm
                    else -> palette.ink
                },
                size = WHEEL_BUTTON_GLYPH,
            )
            Spacer(Modifier.height(6.dp))
            BasicText(
                text = when {
                    !live -> "—"
                    on -> "ON"
                    else -> "OFF"
                },
                style = Type.seatButtonState.copy(
                    color = when {
                        !live -> palette.inkFaint
                        lit -> palette.warm
                        else -> palette.inkMuted
                    },
                ),
            )
        }
    }
}

private val WHEEL_BUTTON_GLYPH = 36.dp

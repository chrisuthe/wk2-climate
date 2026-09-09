package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.Temp
import com.wk2.climate.bus.TempUnit
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.holdRepeatTarget
import com.wk2.climate.ui.rememberPressState
import java.util.Locale

/**
 * The centre column's 131dp bottom row: two temperature zones split by a 1dp
 * divider.
 *
 * **The numeral is a readout, not a button.** Only `−` and `+` are tappable,
 * at 100 x 131 each. That is well over the 96dp floor in both axes, which is
 * the whole point — the OEM bar's equivalents are ~70px tall.
 */
@Composable
fun BarZones(
    palette: Palette,
    driver: Temp,
    passenger: Temp,
    onDriverDown: () -> Unit,
    onDriverUp: () -> Unit,
    onPassengerDown: () -> Unit,
    onPassengerUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zoneWidth = Dimens.barCenterColumn / 2
    Row(
        modifier
            .width(Dimens.barCenterColumn)
            .height(Dimens.barBottomRow),
    ) {
        Zone(
            palette = palette,
            label = "DRIVER",
            temp = driver,
            onDown = onDriverDown,
            onUp = onDriverUp,
            modifier = Modifier.width(zoneWidth).fillMaxHeight(),
        )
        Zone(
            palette = palette,
            label = "PASSENGER",
            temp = passenger,
            onDown = onPassengerDown,
            onUp = onPassengerUp,
            modifier = Modifier
                .width(zoneWidth)
                .fillMaxHeight()
                .sideDivider(palette.divider, start = true),
        )
    }
}

@Composable
private fun Zone(
    palette: Palette,
    label: String,
    temp: Temp,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier) {
        Stepper(palette, "\u2212", palette.cool, onDown)

        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(text = tempText(temp, palette), style = Type.zoneValue)
            Spacer(Modifier.height(2.dp))
            BasicText(
                text = label,
                style = Type.zoneLabel.copy(color = palette.ink.copy(alpha = 0.45f)),
            )
        }

        Stepper(palette, "+", palette.warm, onUp)
    }
}

/**
 * The numeral, with its degree mark at half size.
 *
 * Sentinels never render as a number. [Temp.Lo] and [Temp.Hi] render as
 * `"LOW"` / `"HIGH"` rather than `"LO"` / `"HI"` — that is what the factory
 * UI renders for this vehicle profile, so the driver reads vocabulary they
 * already know. [Temp.Unavailable] renders as an em dash. Showing a driver a
 * number the vehicle never reported is the failure this guards against.
 *
 * Fahrenheit renders as a whole number; Celsius carries a half-degree, so it
 * renders with one decimal.
 */
@Composable
private fun tempText(temp: Temp, palette: Palette): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = palette.ink)) {
            when (temp) {
                is Temp.Degrees -> {
                    append(
                        when (temp.unit) {
                            TempUnit.FAHRENHEIT -> temp.value.toInt().toString()
                            // Locale.US explicitly: the default locale would render a comma
                            // decimal separator in much of Europe, and this is the
                            // untested path.
                            TempUnit.CELSIUS ->
                                String.format(Locale.US, "%.1f", temp.value)
                        },
                    )
                    withStyle(
                        SpanStyle(
                            fontSize = Type.zoneDegree.fontSize,
                            baselineShift = BaselineShift.Superscript,
                        ),
                    ) {
                        append("\u00B0")
                    }
                }
                Temp.Lo -> append("LOW")
                Temp.Hi -> append("HIGH")
                Temp.Unavailable -> append("\u2014")
            }
        }
    }

@Composable
private fun Stepper(
    palette: Palette,
    glyph: String,
    color: Color,
    onFire: () -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    Box(
        Modifier
            .width(Dimens.barStepper)
            .fillMaxHeight()
            .background(if (pressed.value) palette.surfaceRaised else Color.Transparent)
            // One step per tap; press-and-hold repeats at 150ms after 400ms.
            .holdRepeatTarget(interaction, onFire = onFire),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = glyph, style = Type.stepper.copy(color = color))
    }
}

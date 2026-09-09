package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
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

@Composable
fun PanelZones(
    palette: Palette,
    driver: Temp,
    passenger: Temp,
    onDriverDown: () -> Unit,
    onDriverUp: () -> Unit,
    onPassengerDown: () -> Unit,
    onPassengerUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        Zone(palette, "DRIVER", driver, onDriverDown, onDriverUp, Modifier.weight(1f))
        Box(
            Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(palette.divider),
        )
        Zone(palette, "PASSENGER", passenger, onPassengerDown, onPassengerUp, Modifier.weight(1f))
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
    Column(
        modifier.padding(
            start = Dimens.pageGutter,
            end = Dimens.pageGutter,
            top = 30.dp,
            bottom = 34.dp,
        ),
    ) {
        BasicText(text = label, style = Type.sectionHeader.copy(color = palette.inkMuted))
        Spacer(Modifier.height(18.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ZoneStepper(palette, "\u2212", palette.cool, palette.coolBright, onDown)
            BasicText(text = zoneText(temp, palette), style = Type.zoneValueLarge)
            ZoneStepper(palette, "+", palette.warm, palette.warmBright, onUp)
        }

        Spacer(Modifier.height(24.dp))
        RangeTrack(palette, temp)
    }
}

@Composable
private fun ZoneStepper(
    palette: Palette,
    glyph: String,
    tint: Color,
    glyphColor: Color,
    onFire: () -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    Box(
        Modifier
            .size(Dimens.zoneStepper)
            .background(
                tint.copy(alpha = if (pressed.value) 0.34f else 0.18f),
                RoundedCornerShape(Dimens.radiusStepper),
            )
            .border(1.5.dp, tint.copy(alpha = 0.5f), RoundedCornerShape(Dimens.radiusStepper))
            .holdRepeatTarget(interaction, onFire = onFire),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = glyph, style = Type.zoneStepperGlyph.copy(color = glyphColor))
    }
}

/**
 * The numeral, with its degree mark at half size.
 *
 * Sentinels never render as a number: [Temp.Lo] and [Temp.Hi] render as
 * `"LOW"` / `"HIGH"`, matching what screen 2a's bar already renders for this
 * vehicle profile, and [Temp.Unavailable] renders as an em dash.
 */
@Composable
private fun zoneText(temp: Temp, palette: Palette): AnnotatedString =
    buildAnnotatedString {
        withStyle(SpanStyle(color = palette.ink)) {
            when (temp) {
                is Temp.Degrees -> {
                    append(
                        when (temp.unit) {
                            TempUnit.FAHRENHEIT -> temp.value.toInt().toString()
                            // Locale.US explicitly, matching BarZones: the default
                            // locale renders a comma decimal separator in much of
                            // Europe, and Celsius is the untested path here.
                            TempUnit.CELSIUS ->
                                String.format(Locale.US, "%.1f", temp.value)
                        },
                    )
                    withStyle(
                        SpanStyle(
                            fontSize = Type.zoneDegreeLarge.fontSize,
                            baselineShift = BaselineShift.Superscript,
                        ),
                    ) { append("\u00B0") }
                }
                Temp.Lo -> append("LOW")
                Temp.Hi -> append("HIGH")
                Temp.Unavailable -> append("\u2014")
            }
        }
    }

/**
 * Where the setpoint sits in the range. **A readout, not a drag target** --
 * drag-to-set was explored during design and rejected. If it is ever added it
 * must not shrink the 110dp steppers.
 *
 * A sentinel or unknown temperature hides the knob rather than parking it at
 * one end, which would imply a setpoint the vehicle never reported.
 */
@Composable
private fun RangeTrack(palette: Palette, temp: Temp) {
    val fraction = (temp as? Temp.Degrees)
        ?.takeIf { it.unit == TempUnit.FAHRENHEIT }
        ?.let {
            ((it.value - REACHABLE_MIN_F) / (REACHABLE_MAX_F - REACHABLE_MIN_F))
                .coerceIn(0f, 1f)
        }

    Box(Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.CenterStart) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(listOf(palette.trackStart, palette.trackEnd))),
        )
        if (fraction != null) {
            Layout(
                content = {
                    Box(
                        Modifier
                            .size(18.dp)
                            .background(palette.ink, CircleShape)
                            .border(3.dp, palette.surface, CircleShape),
                    )
                },
            ) { measurables, constraints ->
                val knob = measurables.first().measure(constraints)
                layout(constraints.maxWidth, knob.height) {
                    val x = ((constraints.maxWidth - knob.width) * fraction).toInt()
                    knob.placeRelative(x, 0)
                }
            }
        }
    }
}

/**
 * The setpoint range this vehicle can actually reach, measured by sweeping the
 * driver setpoint to both ends: it clamps at 60 and 84 F, then steps into the
 * LOW and HIGH sentinels.
 *
 * Deliberately **not** the protocol's 30..128 window (`Temp.RAW_MIN`/`RAW_MAX`)
 * -- that is what the bus will *accept*, not what this car will produce, and
 * using it would park the knob a third of the way along for a mid-range
 * setpoint. Deliberately not `FakeVehicleBus`'s copy either: production UI must
 * not depend on the test fake.
 */
private const val REACHABLE_MIN_F = 60f
private const val REACHABLE_MAX_F = 84f

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.Fan
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.holdRepeatTarget
import com.wk2.climate.ui.rememberPressState

@Composable
fun PanelFan(
    palette: Palette,
    fan: Fan,
    onDown: () -> Unit,
    onUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(text = "FAN", style = Type.sectionHeader.copy(color = palette.inkMuted))
            BasicText(text = fanReadout(fan, palette), style = Type.fanValue)
        }

        Spacer(Modifier.height(16.dp))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            FanStepper(palette, "\u2212", Type.fanStepperMinus, onDown)
            FanMeter(palette, fan, Modifier.weight(1f))
            FanStepper(palette, "+", Type.fanStepperPlus, onUp)
        }
    }
}

/**
 * `5 / 7`, or `AUTO`.
 *
 * 15 is never rendered as a number: it is the sentinel the vehicle reports
 * while AUTO owns the blower, and measurement showed the fan physically runs
 * at 3 while 15 is reported.
 */
@Composable
private fun fanReadout(fan: Fan, palette: Palette) = buildAnnotatedString {
    when (fan) {
        is Fan.Level -> {
            withStyle(SpanStyle(color = palette.ink)) { append(fan.step.toString()) }
            withStyle(
                SpanStyle(
                    color = palette.inkFaint,
                    fontSize = Type.fanValueDenominator.fontSize,
                    fontWeight = Type.fanValueDenominator.fontWeight,
                ),
            ) { append(" / ${Fan.MAX_STEP}") }
        }
        Fan.Auto -> withStyle(SpanStyle(color = palette.accent)) { append("AUTO") }
        Fan.Unavailable -> withStyle(SpanStyle(color = palette.inkFaint)) { append("\u2014") }
    }
}

@Composable
private fun FanStepper(
    palette: Palette,
    glyph: String,
    style: androidx.compose.ui.text.TextStyle,
    onFire: () -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    Box(
        Modifier
            .width(Dimens.fanStepperWidth)
            .height(Dimens.fanMeterHeight)
            .then(
                if (pressed.value) {
                    Modifier.background(palette.surfaceRaised, RoundedCornerShape(Dimens.radiusTile))
                } else {
                    Modifier
                },
            )
            .border(Dimens.controlBorderWidth, palette.borderControlLarge, RoundedCornerShape(Dimens.radiusTile))
            .holdRepeatTarget(interaction, onFire = onFire),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = glyph, style = style.copy(color = palette.ink))
    }
}

/**
 * One bar per fan level. A readout; the steppers drive the value.
 *
 * Under AUTO every bar lights in the accent colour but dimmed, so the meter
 * reads as "not driver-set" rather than as maximum fan.
 */
@Composable
private fun FanMeter(palette: Palette, fan: Fan, modifier: Modifier = Modifier) {
    // The handoff's 12-bar ramp re-spread across 7 bars.
    val ramp = listOf(0.30f, 0.42f, 0.53f, 0.65f, 0.76f, 0.88f, 1.00f)
    val filled = when (fan) {
        is Fan.Level -> fan.step
        Fan.Auto -> Fan.MAX_STEP
        Fan.Unavailable -> 0
    }
    val auto = fan == Fan.Auto

    Row(
        modifier
            .height(Dimens.fanMeterHeight)
            .background(palette.surfaceRaised, RoundedCornerShape(Dimens.radiusTile))
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .then(if (auto) Modifier.alpha(0.55f) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ramp.forEachIndexed { index, fractionOfHeight ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(fractionOfHeight)
                    .background(
                        if (index < filled) palette.accent else palette.ink.copy(alpha = 0.13f),
                        RoundedCornerShape(Dimens.radiusPip),
                    ),
            )
        }
    }
}

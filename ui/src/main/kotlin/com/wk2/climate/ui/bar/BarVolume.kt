package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The bar's right column: volume up, a readout strip, volume down.
 *
 * The 35dp readout is **deliberately not tappable** and deliberately below the
 * 96dp floor — it is a readout, and making it a target would put a
 * below-minimum control on the bar.
 */
@Composable
fun BarVolume(
    palette: Palette,
    volume: Int?,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(Dimens.barSideColumn)
            .height(Dimens.barHeight)
            .sideDivider(palette.divider, start = true),
    ) {
        VolumeArrow(palette, "\u25B2", onUp)

        Row(
            Modifier
                .fillMaxWidth()
                .height(Dimens.volumeReadout)
                .background(palette.surfaceRaised),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "VOL",
                style = Type.volumeLabel.copy(color = palette.ink.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.width(6.dp))
            BasicText(
                // Never animate the numeral: a moving number is unreadable at a glance.
                text = volume?.toString() ?: "--",
                style = Type.volumeValue.copy(color = palette.ink),
            )
        }

        VolumeArrow(palette, "\u25BC", onDown)
    }
}

@Composable
private fun VolumeArrow(palette: Palette, glyph: String, onClick: () -> Unit) {
    val (interaction, pressed) = rememberPressState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.barTopRow)   // 96dp — the floor
            .background(if (pressed.value) palette.surfaceRaised else Color.Transparent)
            .target(interaction, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(text = glyph, style = Type.volumeArrow.copy(color = palette.ink))
    }
}

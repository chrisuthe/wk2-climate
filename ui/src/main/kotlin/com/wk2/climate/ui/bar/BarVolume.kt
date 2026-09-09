package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
 * The bar's right column: volume up, the readout, volume down.
 *
 * ### Why the readout is drawn rather than laid out
 *
 * The column is 227dp and each arrow must be at least 96dp, so the strip
 * between them is exactly 35dp — there is no spare height to give a bigger
 * number. Sizing the numeral to fit 35dp is what kept it at the handoff's 17px
 * and made it the least legible thing on the bar.
 *
 * So the layout and the painting are separated:
 *
 *  - a **touch layer** keeps the original `96 / 35 / 96` column, so both arrows
 *    still have their full targets and the middle strip is still not tappable;
 *  - a **readout layer** is drawn over the middle, in a band taller than the
 *    strip, with the numeral at 40sp.
 *
 * To stop the number colliding with the arrows, each arrow glyph is aligned to
 * the **outer** edge of its own cell rather than centred in it. The cell keeps
 * its 96dp; only the glyph inside it moves.
 *
 * The consequence worth knowing: the arrows' targets still cover the height the
 * number is drawn over, so a tap on the numeral adjusts the volume — up on its
 * top half, down on its bottom. That is the same thing a tap in the middle of
 * this column always did, and it is a readout with no action of its own to
 * shadow.
 */
@Composable
fun BarVolume(
    palette: Palette,
    volume: Int?,
    onUp: () -> Unit,
    onDown: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .width(Dimens.barSideColumn)
            .height(Dimens.barHeight)
            .sideDivider(palette.divider, start = true),
    ) {
        // --- touch layer: the original 96 / 35 / 96, unchanged ---
        Column(Modifier.fillMaxSize()) {
            VolumeArrow(palette, "\u25B2", Alignment.Top, onUp)
            Spacer(Modifier.fillMaxWidth().height(Dimens.volumeReadout))
            VolumeArrow(palette, "\u25BC", Alignment.Bottom, onDown)
        }

        // --- readout layer: drawn over the middle, not laid out in it ---
        Row(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(Dimens.volumeReadoutBand)
                .background(palette.surfaceRaised)
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "VOL",
                style = Type.volumeLabel.copy(color = palette.ink.copy(alpha = 0.5f)),
            )
            Spacer(Modifier.width(8.dp))
            BasicText(
                // Never animate the numeral: a moving number is unreadable at a glance.
                text = volume?.toString() ?: "--",
                style = Type.volumeValue.copy(color = palette.ink),
            )
        }
    }
}

/**
 * One arrow cell. Keeps the full 96dp target; only the glyph moves.
 *
 * [glyphEdge] pushes the glyph to the outer edge of the cell — top for up,
 * bottom for down — so the readout drawn over the middle has room. The cell
 * itself still fills its 96dp and takes touches across all of it.
 */
@Composable
private fun VolumeArrow(
    palette: Palette,
    glyph: String,
    glyphEdge: Alignment.Vertical,
    onClick: () -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    Column(
        Modifier
            .fillMaxWidth()
            .height(Dimens.barTopRow)
            .background(if (pressed.value) palette.surfaceRaised else Color.Transparent)
            .target(interaction, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = if (glyphEdge == Alignment.Top) {
            Arrangement.Top
        } else {
            Arrangement.Bottom
        },
    ) {
        BasicText(
            text = glyph,
            style = Type.volumeArrow.copy(color = palette.ink),
            modifier = Modifier.padding(vertical = 12.dp),
        )
    }
}

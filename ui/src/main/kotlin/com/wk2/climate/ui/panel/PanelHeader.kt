package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * Screen 1d's header: the "Climate" title, an OUT-only status line, and CLOSE.
 *
 * There is no cabin-temperature signal on this vehicle, so the status line
 * never carries a CABIN reading. [outsideF] being `null` means there is no
 * displayable outside reading right now -- so the whole status line is
 * hidden rather than shown with a placeholder, which would be worse than
 * showing nothing.
 */
@Composable
fun PanelHeader(
    palette: Palette,
    outsideF: Int?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(Dimens.headerHeight)
            .padding(horizontal = Dimens.pageGutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row {
            BasicText(
                text = "Climate",
                style = Type.panelTitle.copy(color = palette.ink),
                modifier = Modifier.alignByBaseline(),
            )
            if (outsideF != null) {
                Spacer(Modifier.width(14.dp))
                BasicText(
                    text = "OUT ${outsideF}\u00B0F",
                    style = Type.panelStatus.copy(color = palette.inkFaint),
                    modifier = Modifier.alignByBaseline(),
                )
            }
        }

        val (interaction, pressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.closeButtonWidth)
                .height(Dimens.minTarget)
                .border(Dimens.controlBorderWidth, palette.borderControlLarge, RoundedCornerShape(Dimens.radiusPill))
                .then(
                    if (pressed.value) {
                        Modifier.background(palette.surfaceRaised, RoundedCornerShape(Dimens.radiusPill))
                    } else {
                        Modifier
                    },
                )
                .target(interaction, onClick = onClose),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                text = "\u25BC",
                style = Type.panelCaret.copy(color = palette.ink.copy(alpha = 0.6f)),
            )
            Spacer(Modifier.width(9.dp))
            BasicText(text = "CLOSE", style = Type.closeLabel.copy(color = palette.ink))
        }
    }
}

package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * Screen 1d's header: the outside temperature, and CLOSE.
 *
 * The "Climate" title and the "OUT" label are both gone at the owner's
 * request. Neither carried information: the page is reached by tapping CLIMATE
 * and fills the screen, so titling it restates what the driver just did, and
 * "OUT" labels the only temperature the header shows. What is left is the
 * reading itself, at the weight the title used to have.
 *
 * There is no cabin-temperature signal on this vehicle, so this is never
 * ambiguous about *which* temperature it is.
 *
 * [outsideF] being `null` means there is no displayable reading. It renders as
 * an em dash rather than an empty header -- the same treatment the setpoints
 * and seat readouts use, and with the title gone an absent reading would
 * otherwise leave the header looking broken rather than uninformed.
 *
 * With more than one of [pages] -- a car whose driver said it has rear
 * climate -- a FRONT | REAR toggle sits between the two, the [page] showing
 * filled. One page draws no toggle, which is every other car's header
 * unchanged.
 */
@Composable
fun PanelHeader(
    palette: Palette,
    outsideF: Int?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    pages: List<PanelPage> = listOf(PanelPage.FRONT),
    page: PanelPage = PanelPage.FRONT,
    onPage: (PanelPage) -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(Dimens.headerHeight)
            .padding(horizontal = Dimens.pageGutter),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            text = if (outsideF != null) "$outsideF\u00B0F" else "\u2014",
            style = Type.panelOutside.copy(color = palette.ink),
        )

        if (pages.size > 1) PageToggle(palette, pages, page, onPage)

        // No border, at the owner's request. The pressed wash is the only
        // affordance left, so it is what tells the driver the tap registered --
        // the 130 x 96 target is unchanged.
        val (interaction, pressed) = rememberPressState()
        Row(
            Modifier
                .width(Dimens.closeButtonWidth)
                .height(Dimens.minTarget)
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

/**
 * The FRONT | REAR toggle: one pill, one half per page, the current half
 * filled. Both halves are full 96dp targets. Tapping the half already showing
 * does nothing.
 */
@Composable
private fun PageToggle(palette: Palette, pages: List<PanelPage>, page: PanelPage, onPage: (PanelPage) -> Unit) {
    val pill = RoundedCornerShape(Dimens.radiusPill)
    Row(
        Modifier
            .height(Dimens.minTarget)
            .clip(pill)
            .border(Dimens.controlBorderWidth, palette.borderControl, pill),
    ) {
        for (p in pages) {
            val current = p == page
            val (interaction, pressed) = rememberPressState()
            Box(
                Modifier
                    .width(PAGE_HALF_WIDTH)
                    .fillMaxHeight()
                    .background(
                        when {
                            current -> palette.accent
                            pressed.value -> palette.surfaceRaised
                            else -> Color.Transparent
                        },
                    )
                    .target(interaction, onClick = { if (!current) onPage(p) }),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = p.name,
                    style = Type.modeLabelLarge.copy(color = if (current) palette.accentInk else palette.inkDim),
                )
            }
        }
    }
}

private val PAGE_HALF_WIDTH = 190.dp

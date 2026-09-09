package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The bar's left column: HOME above BACK, 156 x 113.5 each.
 *
 * Both are comfortably over the 96dp floor. These glyphs are the handoff's
 * placeholders — a bordered square and a chevron, with the text labels doing
 * the work. Real icons are a later choice; what must not ship is a bare
 * rectangle with no label.
 */
@Composable
fun BarNav(
    palette: Palette,
    onHome: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .width(Dimens.barSideColumn)
            .height(Dimens.barHeight),
    ) {
        NavCell(
            palette = palette,
            label = "HOME",
            gap = 7.dp,
            bottomDivider = true,
            onClick = onHome,
        ) {
            Box(
                Modifier
                    .size(28.dp)
                    .border(2.5.dp, palette.ink, RoundedCornerShape(5.dp)),
            )
        }
        NavCell(
            palette = palette,
            label = "BACK",
            gap = 3.dp,
            bottomDivider = false,
            onClick = onBack,
        ) {
            BasicText(
                text = "\u2039",
                style = Type.navChevron.copy(color = palette.ink),
                modifier = Modifier.offset(y = (-4).dp),
            )
        }
    }
}

@Composable
private fun NavCell(
    palette: Palette,
    label: String,
    gap: androidx.compose.ui.unit.Dp,
    bottomDivider: Boolean,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    Column(
        Modifier
            .fillMaxWidth()
            .height(Dimens.barNavRow)
            .then(if (bottomDivider) Modifier.bottomDivider(palette.divider) else Modifier)
            .background(if (pressed.value) palette.surfaceRaised else Color.Transparent)
            .target(interaction, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        glyph()
        androidx.compose.foundation.layout.Spacer(Modifier.height(gap))
        BasicText(
            text = label,
            style = Type.navLabel.copy(color = palette.ink.copy(alpha = 0.7f)),
        )
    }
}

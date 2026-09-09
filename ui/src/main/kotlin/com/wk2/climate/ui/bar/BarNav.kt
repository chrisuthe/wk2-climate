package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.wk2.climate.ui.HomeGlyph
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * The bar's left column: HOME above BACK, 156 x 113.5 each.
 *
 * Both are comfortably over the 96dp floor.
 *
 * HOME is `akar-icons:home`, drawn as a stroked path so one drawing serves both
 * themes and the difference is the tint. BACK is still the handoff's chevron
 * placeholder, with its text label doing the work.
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
            HomeGlyph(tint = palette.ink, size = 32.dp)
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

package com.wk2.climate.ui.panel

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type

/**
 * One section of screen 1d: a 1dp top border, an optional micro-header, then
 * content.
 *
 * Sections are separated by a border rather than by gaps, which is what lets
 * the page's child heights sum to a known total and the footer pin to the
 * bottom with the remaining space.
 */
@Composable
fun PanelSection(
    palette: Palette,
    header: String?,
    modifier: Modifier = Modifier,
    topBorder: Boolean = true,
    /**
     * The handoff's section padding, `20px 34px 24px`.
     *
     * Overridable because the temperature-zone section must pass **zero**: each
     * zone carries its own `30/34/34` padding, and that per-zone padding *is*
     * the page gutter. Applying both would put the gutter at 68dp and squeeze
     * the 110dp steppers and the 96px numeral into ~944dp of a 1080dp page.
     */
    contentPadding: PaddingValues = PaddingValues(
        start = Dimens.pageGutter,
        end = Dimens.pageGutter,
        top = 20.dp,
        bottom = 24.dp,
    ),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .then(if (topBorder) Modifier.topDivider(palette.divider) else Modifier)
            .padding(contentPadding),
    ) {
        if (header != null) {
            BasicText(
                text = header,
                style = Type.sectionHeader.copy(color = palette.inkMuted),
            )
            Spacer(Modifier.height(16.dp))
        }
        content()
    }
}

/**
 * A 1dp divider along the top edge.
 *
 * Drawn rather than laid out, so it never participates in measurement — the
 * same reason the bar's dividers are drawn.
 */
fun Modifier.topDivider(color: Color): Modifier = drawBehind {
    val h = 1.dp.toPx()
    drawRect(color = color, topLeft = Offset(0f, 0f), size = Size(size.width, h))
}

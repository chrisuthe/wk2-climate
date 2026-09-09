package com.wk2.climate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * The HOME glyph: `akar-icons:home`, MIT licensed.
 *
 * Drawn rather than shipped as a bitmap, for the same reason
 * [com.wk2.climate.ui.panel.RearDefrostGlyph] is: one drawing serves both
 * themes and every state, and the difference is the [tint]. A stroked outline
 * also stays crisp at any size, where a raster tuned for 28dp would not.
 *
 * Replaces the handoff's placeholder, which was a bordered rounded square --
 * explicitly a stand-in, with the text label doing the work.
 *
 * The path is the icon's own `d` attribute, unmodified, in its 24x24 viewport.
 * It is parsed once per composition rather than per frame, and the stroke width
 * is given in **viewport units** so the canvas scale carries it: 2 units of 24
 * lands at 2.67dp when drawn at 32dp, which is close to the 2.5dp border the
 * placeholder used.
 */
@Composable
fun HomeGlyph(
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val path = remember { PathParser().parsePathString(HOME_PATH).toPath() }
    Canvas(modifier.size(size)) {
        val scale = this.size.minDimension / VIEWPORT
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            drawPath(
                path = path,
                color = tint,
                style = Stroke(
                    width = STROKE_UNITS,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/** `akar-icons:home`'s `d`, verbatim. Open stroke, not a filled shape. */
private const val HOME_PATH =
    "M21 19v-6.733a4 4 0 0 0-1.245-2.9L13.378 3.31a2 2 0 0 0-2.755 0L4.245 " +
        "9.367A4 4 0 0 0 3 12.267V19a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2"

private const val VIEWPORT = 24f
private const val STROKE_UNITS = 2f

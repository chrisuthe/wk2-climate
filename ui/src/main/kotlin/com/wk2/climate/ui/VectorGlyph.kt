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
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * Draws an SVG path from an icon set, scaled into [size] and tinted.
 *
 * Every glyph in this app that is not a bitmap goes through here. Drawing
 * rather than shipping rasters is the house convention: **one drawing serves
 * both palettes and every state, and the difference is the [tint]** — so a
 * control that is off, on, or unknown needs one asset, not three. An outline
 * also stays crisp at any size, where a raster tuned for 24dp would not.
 *
 * [pathData] is an icon's `d` attribute used **verbatim**, in its own
 * viewport. That is deliberate: these paths carry dozens of arc and curve
 * segments, and hand-transcribing SVG arcs into `arcTo` calls means
 * reimplementing the endpoint-to-centre parameterisation — a well-known source
 * of shapes that are subtly wrong. `PathParser` is already on the classpath via
 * `compose-ui` and takes the attribute as-is, so there is nothing to get wrong.
 *
 * The path is parsed **once per composition**, not per frame.
 *
 * ### Stroke widths are in viewport units
 *
 * [strokeWidth] is measured in the path's own units, because the canvas is
 * scaled by `size / viewport` and that scale carries the stroke with it. Two
 * units of a 24-unit viewport drawn at 32dp lands at 2.67dp. Passing a dp value
 * here would be multiplied by the scale a second time.
 *
 * A null [strokeWidth] fills instead, with the default non-zero winding rule —
 * which is what leaves interior detail such as wheel spokes open rather than
 * flooding the shape.
 */
@Composable
fun VectorGlyph(
    pathData: String,
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    viewport: Float = 24f,
    strokeWidth: Float? = null,
) {
    val path = remember(pathData) { PathParser().parsePathString(pathData).toPath() }
    val style: DrawStyle = remember(strokeWidth) {
        if (strokeWidth == null) {
            Fill
        } else {
            Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        }
    }
    Canvas(modifier.size(size)) {
        val scale = this.size.minDimension / viewport
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            drawPath(path = path, color = tint, style = style)
        }
    }
}

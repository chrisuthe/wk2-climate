package com.wk2.climate.ui.panel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Rear-window defrost.
 *
 * Drawn rather than shipped as an asset because the supplied automotive icon
 * set has no rear-defrost glyph. Stroke weight is tuned to sit alongside the
 * bitmap glyphs rather than to any independent standard.
 */
@Composable
fun RearDefrostGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(width = 50.dp, height = 44.dp)) {
        val stroke = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
        val inset = stroke.width / 2f

        // The window outline.
        drawRoundRect(
            color = tint,
            topLeft = Offset(inset, inset),
            size = Size(size.width - stroke.width, size.height - stroke.width),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            style = stroke,
        )

        // Three heat waves rising through it.
        val left = size.width * 0.28f
        val right = size.width * 0.72f
        val amplitude = size.width * 0.07f
        listOf(0.34f, 0.54f, 0.74f).forEach { yFraction ->
            val y = size.height * yFraction
            val path = Path().apply {
                moveTo(left, y)
                cubicTo(
                    left + (right - left) * 0.33f, y - amplitude,
                    left + (right - left) * 0.66f, y + amplitude,
                    right, y,
                )
            }
            drawPath(path, color = tint, style = stroke)
        }
    }
}

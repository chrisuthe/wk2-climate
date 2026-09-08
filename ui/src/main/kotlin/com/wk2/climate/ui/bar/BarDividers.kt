package com.wk2.climate.ui.bar

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A 1dp divider along the bottom edge.
 *
 * Drawn with `drawBehind` rather than as a layout node, so a 1dp line never
 * participates in measurement and cannot shift a control by a pixel.
 */
fun Modifier.bottomDivider(color: Color): Modifier = drawBehind {
    val h = 1.dp.toPx()
    drawRect(color = color, topLeft = Offset(0f, size.height - h), size = Size(size.width, h))
}

/** A 1dp divider along the given vertical edge. */
fun Modifier.sideDivider(color: Color, start: Boolean): Modifier = drawBehind {
    val w = 1.dp.toPx()
    drawRect(
        color = color,
        topLeft = Offset(if (start) 0f else size.width - w, 0f),
        size = Size(w, size.height),
    )
}

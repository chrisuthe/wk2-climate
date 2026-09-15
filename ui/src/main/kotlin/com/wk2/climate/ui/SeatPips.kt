package com.wk2.climate.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.wk2.climate.design.Palette

/**
 * The two-pip level indicator, shared by screen 1d's seat tiles and screen
 * 2a's seat buttons.
 *
 * Two pips carry three states: none lit is OFF, one is LOW, both are HIGH.
 * That is not a compromise -- the vehicle's own cycle is `0 -> 3 -> 1 -> 0`
 * and never visits 2, so every tap changes what the driver sees. Takes the
 * lit count rather than a `SeatLevel` so the bar button, which has already
 * decided between heat and cool, can pass whichever level it chose.
 *
 * The caller sizes it: the panel's tiles let each pip take half the row
 * ([pipWidth] null, so `weight(1f)`) at 10dp tall with an 8dp gap; the bar's
 * 128dp button fixes them at 14 x 6 with a 4dp gap. Extracted so the two
 * share one definition rather than mirroring a copy.
 */
@Composable
fun SeatPips(
    palette: Palette,
    litPips: Int,
    litColor: Color,
    pipHeight: Dp,
    gap: Dp,
    modifier: Modifier = Modifier,
    pipWidth: Dp? = null,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        repeat(2) { index ->
            Box(
                Modifier
                    .then(if (pipWidth != null) Modifier.width(pipWidth) else Modifier.weight(1f))
                    .height(pipHeight)
                    .background(
                        if (index < litPips) litColor else palette.ink.copy(alpha = 0.13f),
                        RoundedCornerShape(pipHeight / 2),
                    ),
            )
            if (index == 0) Spacer(Modifier.width(gap))
        }
    }
}

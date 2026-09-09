package com.wk2.climate.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import com.wk2.climate.design.R

/**
 * The **bitmap** glyph assets, from a conventional automotive HVAC set.
 *
 * Only the ones with no vector source live here. Everything drawn is in
 * [Glyphs] instead -- see [VectorGlyph] for why. `defrost` used to be here and
 * was replaced by `material-symbols-light:hvac-max-defrost`.
 */
object Glyph {
    val recirc = R.drawable.ic_recirc
    val face = R.drawable.ic_face
    val faceFeet = R.drawable.ic_face_feet
    val feet = R.drawable.ic_feet
    val feetGlass = R.drawable.ic_feet_glass
}

/**
 * A glyph drawn at a fixed height with its width free, tinted at runtime.
 *
 * The assets are light glyphs on transparency, so one bitmap serves both
 * themes and both active and inactive states — the difference is [tint], not
 * a different file.
 */
@Composable
fun GlyphIcon(
    @DrawableRes res: Int,
    tint: Color,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(res),
        contentDescription = null,
        modifier = modifier.height(height),
        contentScale = ContentScale.FillHeight,
        colorFilter = ColorFilter.tint(tint),
    )
}

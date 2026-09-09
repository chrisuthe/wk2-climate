package com.wk2.climate.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp

/**
 * The heated-steering-wheel glyph: `material-symbols:steering-wheel-heat`,
 * Apache 2.0.
 *
 * Replaces a plain circle that carried its meaning entirely through three fill
 * states. This one is a wheel with heat lines, so it says what it controls
 * before the label is read.
 *
 * **A filled glyph changes how the three states have to be expressed.** The
 * circle distinguished them structurally — a filled disc for on, a ring for
 * off, a faint ring for unknown. A filled path has no ring to fall back on, so
 * the difference becomes the [tint] alone, which is the convention the rest of
 * the icon set already follows:
 *
 *  - **on** -> `warm` / `warmBright`, saturated, the only state that claims heat
 *  - **off** -> `ink`, neutral and fully legible: a control we know is off
 *  - **unknown** -> `inkFaint`, matching every other absent reading
 *
 * That keeps off and unknown distinguishable, which was the point of the
 * original faint-versus-warm ring choice, and stops a warm glyph reading as
 * "heating" when it is merely resting.
 *
 * Drawn rather than shipped as a bitmap, like [HomeGlyph] and
 * [com.wk2.climate.ui.panel.RearDefrostGlyph]: one drawing serves both palettes
 * and all three states.
 *
 * The path is the icon's own `d` attribute, unmodified, in its 24x24 viewport,
 * parsed once per composition rather than per frame. It is a **fill** with the
 * default non-zero winding rule, which is what leaves the wheel's spokes and
 * the gaps in the heat lines open.
 */
@Composable
fun WheelHeatGlyph(
    tint: Color,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val path = remember { PathParser().parsePathString(WHEEL_HEAT_PATH).toPath() }
    Canvas(modifier.size(size)) {
        val scale = this.size.minDimension / VIEWPORT
        withTransform({ scale(scale, scale, pivot = Offset.Zero) }) {
            drawPath(path = path, color = tint, style = Fill)
        }
    }
}

/** `material-symbols:steering-wheel-heat`'s `d`, verbatim. */
private const val WHEEL_HEAT_PATH =
    "m22.025 9.55l-1.65-1.1l.325-.525q.15-.225.225-.462T21 6.95q0-.35-.125-" +
        ".675T20.5 5.7q-.525-.525-.812-1.213T19.4 3.05q0-.575.163-1.1t.462-1l" +
        ".325-.5l1.675 1.1l-.35.525q-.15.225-.213.463t-.062.512q0 .35.125.675" +
        "t.375.575q.525.525.813 1.213T23 6.95q0 .575-.162 1.1t-.463 1zm-4.2 0" +
        "l-1.65-1.1l.325-.525q.15-.225.225-.462t.075-.513q0-.35-.125-.675T16.3" +
        " 5.7q-.525-.525-.812-1.213T15.2 3.05q0-.575.163-1.1t.462-1l.325-.5l1." +
        "675 1.1l-.35.525q-.15.225-.213.463t-.062.512q0 .35.125.675t.375.575q." +
        "525.525.813 1.213T18.8 6.95q0 .575-.162 1.1t-.463 1zM13 19.925q2.725-" +
        ".35 4.65-2.275T19.925 13H16l-3 3.75zm.65-10.375l-1.675-1.1l.35-.525q." +
        "15-.225.225-.462t.075-.513q0-.35-.138-.675T12.1 5.7q-.525-.525-.812-1" +
        ".213T11 3.05q0-.575.15-1.1t.475-1l.35-.5l1.675 1.1l-.35.525q-.15.2-." +
        "225.45T13 3.05q0 .35.125.675t.375.575q.525.525.813 1.213T14.6 6.95q0 " +
        ".575-.162 1.1t-.463 1zM4.075 13q.35 2.725 2.275 4.65T11 19.925V16.75L" +
        "8 13zm0-2H21.95q.025.25.038.5T22 12q0 2.075-.787 3.9t-2.138 3.175t-3." +
        "175 2.138T12 22t-3.9-.788t-3.175-2.137T2.788 15.9T2 12q0-3.375 1.975-" +
        "5.988T9 2.45V4.6q-1.975.8-3.312 2.488T4.075 11"

private const val VIEWPORT = 24f

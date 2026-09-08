package com.wk2.climate.design

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The handoff's type scale, expressed once.
 *
 * Two families: **Manrope** for UI, values and labels; **IBM Plex Mono** for
 * micro-labels, states and numerics, always uppercase with wide tracking.
 *
 * `1px = 1dp` on this panel, and Compose `sp` follows the user's font scale —
 * which on a head unit is always 1.0 and which we must not let move a control
 * anyway. Sizes are given in `sp` because that is what `TextStyle` takes; the
 * fixed-geometry rule is enforced by the *containers*, never by text metrics.
 */
object Type {

    val manrope = FontFamily(Font(R.font.manrope))

    val plexMono = FontFamily(
        Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
        Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
        // Plex Mono has no weight above Bold; the design's 800 lands here.
        Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
    )

    private fun ui(
        size: TextUnit,
        weight: FontWeight,
        tracking: TextUnit = 0.sp,
    ) = TextStyle(
        fontFamily = manrope,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = tracking,
    )

    private fun mono(
        size: TextUnit,
        weight: FontWeight,
        tracking: TextUnit,
    ) = TextStyle(
        fontFamily = plexMono,
        fontSize = size,
        fontWeight = weight,
        letterSpacing = tracking,
    )

    // ---- screen 2a ----

    /** HOME / BACK micro-labels. IBM Plex Mono 600 11px, ls .1em. */
    val navLabel = mono(11.sp, FontWeight.SemiBold, 1.1.sp)

    /** BACK chevron. Manrope 300 32px. */
    val navChevron = ui(32.sp, FontWeight.Light)

    /** WHEEL / CLIMATE labels. Manrope 700 16px, ls .04em. */
    val barControlLabel = ui(16.sp, FontWeight.Bold, 0.64.sp)

    /** AUTO. Manrope 800 19px, ls .06em. */
    val barAuto = ui(19.sp, FontWeight.ExtraBold, 1.14.sp)

    /** CLIMATE's ▲. Manrope 400 13px. */
    val barCaret = ui(13.sp, FontWeight.Normal)

    /** Adaptive slot title. Manrope 800 16px, ls .04em. */
    val slotLabel = ui(16.sp, FontWeight.ExtraBold, 0.64.sp)

    /** Adaptive slot's defrost label. Manrope 800 15px, ls .04em. */
    val slotLabelSmall = ui(15.sp, FontWeight.ExtraBold, 0.6.sp)

    /** Adaptive slot OFF / LOW / HIGH. IBM Plex Mono 800 11px, ls .14em. */
    val slotState = mono(11.sp, FontWeight.Bold, 1.54.sp)

    /** Zone temperature numeral. Manrope 700 48px, ls -0.03em. */
    val zoneValue = ui(48.sp, FontWeight.Bold, (-1.44).sp)

    /** The degree mark beside it, 24px. */
    val zoneDegree = ui(24.sp, FontWeight.Bold)

    /** DRIVER / PASSENGER. IBM Plex Mono 600 10px, ls .14em. */
    val zoneLabel = mono(10.sp, FontWeight.SemiBold, 1.4.sp)

    /** The − / + glyphs. Manrope 300 42px. */
    val stepper = ui(42.sp, FontWeight.Light)

    /** Volume ▲ / ▼. Manrope 400 24px. */
    val volumeArrow = ui(24.sp, FontWeight.Normal)

    /** "VOL". IBM Plex Mono 600 10px, ls .12em. */
    val volumeLabel = mono(10.sp, FontWeight.SemiBold, 1.2.sp)

    /** The volume number. Manrope 700 17px. */
    val volumeValue = ui(17.sp, FontWeight.Bold)
}

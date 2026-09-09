@file:OptIn(ExperimentalTextApi::class)

package com.wk2.climate.design

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
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

    // A bare Font(resId) registers one static face and never samples the
    // variable font's `wght` axis for anything but Normal, so every weight
    // this scale uses gets its own entry with an explicit variation setting.
    val manrope = FontFamily(
        Font(
            resId = R.font.manrope,
            weight = FontWeight.Light,
            variationSettings = FontVariation.Settings(FontVariation.weight(300)),
        ),
        Font(
            resId = R.font.manrope,
            weight = FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            resId = R.font.manrope,
            weight = FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
        Font(
            resId = R.font.manrope,
            weight = FontWeight.ExtraBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(800)),
        ),
    )

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

    /**
     * The volume number.
     *
     * Much larger than the handoff's 17px, which sized it to fit inside the
     * 35dp readout strip. The strip cannot grow -- it is exactly what is left
     * of the 227dp column after two 96dp touch floors -- so instead the
     * readout is **drawn over** the column rather than laid out inside the
     * strip, and the arrows move to the outer edges of their cells to make
     * room. Touch targets are untouched; see `BarVolume`.
     */
    val volumeValue = ui(40.sp, FontWeight.Bold, (-0.5).sp)

    // ---- screen 1d ----

    /** Every section header. IBM Plex Mono 600 12px, ls .16em. */
    val sectionHeader = mono(12.sp, FontWeight.SemiBold, 1.92.sp)

    /** "Climate". Manrope 800 30px, ls -0.01em. */
    val panelTitle = ui(30.sp, FontWeight.ExtraBold, (-0.3).sp)

    /** "OUT 41°F". IBM Plex Mono 500 15px. */
    val panelStatus = mono(15.sp, FontWeight.Medium, 0.sp)

    /** Screen 1d's CLOSE ▼. Manrope 400 15px -- not [barCaret], which is screen 2a's 13px. */
    val panelCaret = ui(15.sp, FontWeight.Normal)

    /** CLOSE. Manrope 700 16px. */
    val closeLabel = ui(16.sp, FontWeight.Bold)

    /** The big zone numeral. Manrope 800 96px, ls -0.05em. */
    val zoneValueLarge = ui(96.sp, FontWeight.ExtraBold, (-4.8).sp)

    /** Its degree mark, 38px. */
    val zoneDegreeLarge = ui(38.sp, FontWeight.ExtraBold)

    /** The 110dp steppers' − / +. Manrope 300 52px. */
    val zoneStepperGlyph = ui(52.sp, FontWeight.Light)

    /** Fan value. Manrope 700 20px. */
    val fanValue = ui(20.sp, FontWeight.Bold)

    /** The "/ 7" after it. Manrope 500 14px. */
    val fanValueDenominator = ui(14.sp, FontWeight.Medium)

    /** Fan − / +. Manrope 300 46px. */
    /**
     * Screen 1d's fan steppers, which are **not** the same size: the handoff
     * gives `−` 46px and `+` 42px. That asymmetry is optical correction, not
     * an inconsistency — a minus is a single thin bar and reads lighter than a
     * plus of equal height, so it is set larger to match its weight. Screen
     * 2a's bar steppers are both 42px and use their own styles.
     */
    val fanStepperMinus = ui(46.sp, FontWeight.Light)
    val fanStepperPlus = ui(42.sp, FontWeight.Light)

    /**
     * 1d's header reading -- the outside temperature, which is the whole
     * header now that the title and the OUT label are gone.
     *
     * Larger than [panelTitle], which it replaced: with nothing beside it the
     * old 30sp read small for the only thing on the row.
     */
    val panelOutside = ui(38.sp, FontWeight.ExtraBold, (-0.4).sp)

    /** AUTO / A/C tiles. Manrope 800 19px, ls .05em. */
    val modeLabelLarge = ui(19.sp, FontWeight.ExtraBold, 0.95.sp)

    /** RECIRC / MAX A/C / SYNC. Manrope 800 17px, ls .05em. */
    val modeLabelSmall = ui(17.sp, FontWeight.ExtraBold, 0.85.sp)

    /** FRONT DEF / REAR DEF. Manrope 800 15px, ls .04em. */
    val modeLabelTiny = ui(15.sp, FontWeight.ExtraBold, 0.6.sp)

    /** SYNC's "DUAL" sub-label. IBM Plex Mono 500 12px. */
    val syncSub = mono(12.sp, FontWeight.Medium, 0.sp)


    /** OFF / LOW / HIGH. IBM Plex Mono 800 13px, ls .12em. */
    val comfortState = mono(13.sp, FontWeight.Bold, 1.56.sp)

    /** HEATED STEERING WHEEL. Manrope 800 18px, ls .05em. */
    val wheelLabel = ui(18.sp, FontWeight.ExtraBold, 0.9.sp)


    /** HOLD · OFF. Manrope 700 15px, ls .06em. */
    val holdOffLabel = ui(15.sp, FontWeight.Bold, 0.9.sp)
}

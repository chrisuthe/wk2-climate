package com.wk2.climate.design

import androidx.compose.ui.unit.dp

/**
 * Fixed geometry.
 *
 * The panel is 1080x1920 at 160dpi, so 1px = 1dp and every measurement in the
 * design handoff is used directly.
 *
 * A 96dp floor on every tappable region is the point of the redesign — the OEM
 * bar packs ~24 same-weight targets into three rows, none taller than ~70px.
 * If an implementation detail forces a tradeoff, this is what must survive.
 */
object Dimens {
    /** The absolute minimum for anything tappable. Not 88. Not 72. */
    val minTarget = 96.dp

    // ---- screen 2a: the resting bar ----
    // Design rule 5: the bar rests here and never grows. This is the source of
    // truth for the window's height — the framework's navigation_bar_height is
    // checked *against* it (see ClimateBarService.warnIfNavInsetDisagrees), not
    // the other way round.
    val barHeight = 227.dp
    val barWidth = 1080.dp
    val barSideColumn = 156.dp
    val barCenterColumn = 768.dp
    val barTopRow = 96.dp
    val barBottomRow = 131.dp
    val barNavRow = 113.5.dp
    val slotWheel = 210.dp
    val slotAdaptive = 200.dp         // the only region whose contents change
    val slotAuto = 174.dp
    val slotClimate = 184.dp
    val barStepper = 100.dp
    val volumeReadout = 35.dp         // deliberately below minTarget: not tappable

    // ---- screen 1d: the climate page ----
    val pageWidth = 1080.dp
    val pageHeight = 1693.dp          // exactly the inset-reduced app area
    val pageGutter = 34.dp
    val headerHeight = 112.dp
    val closeButtonWidth = 130.dp
    val zoneStepper = 110.dp
    val fanStepperWidth = 104.dp
    val fanMeterHeight = 96.dp
    val airflowTileHeight = 136.dp
    /**
     * Both mode grids. The handoff gives them the same height — README's "Two
     * grids, gap 14px, tiles height 104", and every tile in 1d's markup
     * (AUTO/A/C/RECIRC/MAX A/C and FRONT DEF/REAR DEF/SYNC) is `height:104px`.
     *
     * This was two constants, and the second held 96, so the lower grid drew
     * 8dp short. One name for one measurement: two names for the same value is
     * how the wrong one hid.
     */
    val modeTileHeight = 104.dp
    val comfortTileHeight = 96.dp
    val holdOffWidth = 180.dp

    /**
     * The gap between page-level tiles on screen 1d — the airflow row, both
     * mode grids (column *and* row gap), the comfort grid and the heated-wheel
     * tile below it, and the fan stepper/meter row. The handoff writes `gap:14px`
     * on every one of them.
     *
     * Deliberately **not** applied to the 14px gaps *inside* a tile (a seat
     * tile's pip group to its state word, the wheel's ring to its label) or to
     * the header's title-to-status gap. Those are separate measurements that
     * happen to equal 14 today, and collapsing them onto this name would make
     * a retune of the tile grid silently move a tile's own contents.
     */
    val tileGap = 14.dp

    /** The outlined-control border width used across screen 1d's controls. */
    val controlBorderWidth = 1.5.dp

    // ---- radii ----
    val radiusPip = 3.dp
    val radiusSmall = 5.dp
    val radiusTile = 18.dp
    val radiusStepper = 20.dp
    val radiusPill = 26.dp

    // ---- interaction timing ----
    const val HOLD_REPEAT_DELAY_MS = 400L
    const val HOLD_REPEAT_INTERVAL_MS = 150L
    const val POWER_HOLD_MS = 800L
    const val PANEL_TRANSITION_MS = 220
}

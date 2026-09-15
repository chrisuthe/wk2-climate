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
    // The top row, left to right. These four must sum to [barCenterColumn] --
    // asserted in ui's BarGeometryTest, because an overflow here would push
    // CLIMATE off the edge silently rather than failing.
    //
    // Mirror-symmetric about the centre divider, like the zones beneath: a
    // seat button at each end, AUTO and CLIMATE between them. Nothing in this
    // row changes what it holds any more -- the two adaptive cells that used
    // to sit between AUTO and CLIMATE are gone, and every cell can be found
    // by feel.
    val seatButton = 128.dp
    val slotAuto = 256.dp
    val slotAdaptiveFirst = 200.dp     // retired in the next commit
    val slotAdaptiveSecond = 174.dp    // retired in the next commit
    val slotClimate = 256.dp

    // ---- the seat menus, which float above the bar in their own window ----
    // Measured from a screenshot of the design at ~1.25 px/dp, so approximate;
    // where the design file disagrees, the file wins. The anchors are exact:
    // the driver menu is left-aligned to the driver button, which is the bar's
    // left column, and the passenger menu is right-aligned to the passenger
    // button, which is the centre column's right edge.
    val seatMenuWidth = 360.dp
    val seatMenuRow = 96.dp
    val seatMenuDriverX = barSideColumn
    val seatMenuPassengerX = barSideColumn + barCenterColumn - seatMenuWidth
    val seatMenuIconSize = 28.dp
    val seatMenuIconInset = 27.dp     // icon's left edge from the menu's left edge
    val seatMenuLabelX = 74.dp        // label's left edge from the menu's left edge
    val seatMenuStateInset = 24.dp    // state text's right edge from the menu's right edge
    val barStepper = 100.dp
    val volumeReadout = 35.dp         // deliberately below minTarget: not tappable

    /**
     * The readout's **drawn** band, which is taller than [volumeReadout].
     *
     * The laid-out strip is fixed at 35dp: the 227dp column has to give 96dp
     * to each arrow, and 35 is the remainder. So the visible band is painted
     * by an overlay that is not part of that column, letting the number be
     * legible at a glance without any control dropping below the floor.
     */
    val volumeReadoutBand = 78.dp

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
    val radiusMenu = 16.dp

    // ---- interaction timing ----
    const val HOLD_REPEAT_DELAY_MS = 400L
    const val HOLD_REPEAT_INTERVAL_MS = 150L
    const val POWER_HOLD_MS = 800L
    const val PANEL_TRANSITION_MS = 220
}

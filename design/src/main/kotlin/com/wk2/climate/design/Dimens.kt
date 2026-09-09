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
    val modeTileRow1 = 104.dp
    val modeTileRow2 = 96.dp
    val comfortTileHeight = 96.dp
    val holdOffWidth = 180.dp

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

    /** How long to wait for the vehicle before resolving a pressed state anyway. */
    const val COMMAND_SETTLE_MS = 600L
}

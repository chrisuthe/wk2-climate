package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.SeatCoolLeftGlyph
import com.wk2.climate.ui.SeatCoolRightGlyph
import com.wk2.climate.ui.SeatHeatLeftGlyph
import com.wk2.climate.ui.SeatHeatRightGlyph
import com.wk2.climate.ui.SeatPips
import com.wk2.climate.ui.SeatPlainLeftGlyph
import com.wk2.climate.ui.SeatPlainRightGlyph
import com.wk2.climate.ui.WheelHeatGlyph
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/**
 * A seat button: one of the two 128 x 96 cells at the ends of the bar's top
 * row. It **shows** the seat's state and **opens** that seat's menu; it never
 * dispatches a vehicle command itself.
 *
 * What it shows is decided by [SeatIndicator], not here: a heat or cool
 * variant of the seat glyph in its lit colour with one or two pips beneath,
 * the plain seat with `OFF` when both levels are reported off, and the plain
 * seat with an em dash when they are not reported. The glyph's silhouette is
 * the same in every state (see `SeatPlainLeftGlyph`), and the readout row
 * under it has a fixed height, so nothing moves when the state changes.
 *
 * Driver uses the LEFT glyph variants and passenger the RIGHT, as the panel's
 * comfort tiles do. The driver's button also carries the heated-wheel badge
 * in its top-right corner when the wheel is on -- independent of the seat, so
 * an OFF seat with the wheel on shows the plain seat, `OFF`, and the badge.
 * [wheelOn] is passed already gated on `live` by the caller, like every other
 * wheel flag in the bar.
 *
 * While its menu is up the cell's background is `surfaceRaised`, the same
 * treatment as the pressed state, so the driver can see which side is open.
 */
@Composable
fun SeatButton(
    palette: Palette,
    side: SeatSide,
    indicator: SeatIndicator,
    wheelOn: Boolean,
    menuOpen: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (interaction, pressed) = rememberPressState()
    val tint = when (indicator.kind) {
        SeatIndicator.Kind.HEAT -> palette.warm
        SeatIndicator.Kind.COOL -> palette.cool
        SeatIndicator.Kind.OFF -> palette.ink
        SeatIndicator.Kind.UNKNOWN -> palette.inkFaint
    }

    Box(
        modifier
            .width(Dimens.seatButton)
            .fillMaxHeight()
            .background(
                if (menuOpen || pressed.value) palette.surfaceRaised else Color.Transparent,
            )
            .target(interaction, onClick = onClick),
    ) {
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SeatGlyph(side, indicator.kind, tint, SEAT_BUTTON_GLYPH)
            Spacer(Modifier.height(6.dp))
            // Fixed height whether it holds pips or a word, so the glyph
            // above it sits at the same y in every state.
            Box(Modifier.height(READOUT_HEIGHT), contentAlignment = Alignment.Center) {
                when (indicator.kind) {
                    SeatIndicator.Kind.HEAT, SeatIndicator.Kind.COOL -> SeatPips(
                        palette, indicator.litPips, tint,
                        pipHeight = 6.dp, gap = 4.dp, pipWidth = 14.dp,
                    )
                    SeatIndicator.Kind.OFF -> BasicText(
                        text = "OFF",
                        style = Type.seatButtonState.copy(color = palette.inkMuted),
                    )
                    // An em dash, never OFF: one spelling of "we do not know"
                    // across the bar, matching Temp.Unavailable in the zones.
                    SeatIndicator.Kind.UNKNOWN -> BasicText(
                        text = "—",
                        style = Type.seatButtonState.copy(color = palette.inkFaint),
                    )
                }
            }
        }

        if (side == SeatSide.DRIVER && wheelOn) {
            WheelHeatGlyph(
                tint = palette.warm,
                size = WHEEL_BADGE,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 6.dp),
            )
        }
    }
}

@Composable
private fun SeatGlyph(side: SeatSide, kind: SeatIndicator.Kind, tint: Color, size: Dp) {
    val driver = side == SeatSide.DRIVER
    when (kind) {
        SeatIndicator.Kind.HEAT ->
            if (driver) SeatHeatLeftGlyph(tint, size) else SeatHeatRightGlyph(tint, size)
        SeatIndicator.Kind.COOL ->
            if (driver) SeatCoolLeftGlyph(tint, size) else SeatCoolRightGlyph(tint, size)
        SeatIndicator.Kind.OFF, SeatIndicator.Kind.UNKNOWN ->
            if (driver) SeatPlainLeftGlyph(tint, size) else SeatPlainRightGlyph(tint, size)
    }
}

/** Spec §3: approx 36dp, centred, upper part of the cell. */
private val SEAT_BUTTON_GLYPH = 36.dp

/** Spec §3: approx 32dp, top-right, driver only. */
private val WHEEL_BADGE = 32.dp

/** Tall enough for the 11sp state word; the 6dp pips centre inside it. */
private val READOUT_HEIGHT = 14.dp

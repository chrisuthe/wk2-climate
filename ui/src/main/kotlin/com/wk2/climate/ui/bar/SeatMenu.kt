package com.wk2.climate.ui.bar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.SeatLevel
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.SeatCoolLeftGlyph
import com.wk2.climate.ui.SeatCoolRightGlyph
import com.wk2.climate.ui.SeatHeatLeftGlyph
import com.wk2.climate.ui.SeatHeatRightGlyph
import com.wk2.climate.ui.WheelHeatGlyph
import com.wk2.climate.ui.pressedTint
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

/** Rows in a side's menu: the driver's has the heated wheel, the passenger's does not. */
fun seatMenuRows(side: SeatSide): Int = when (side) {
    SeatSide.DRIVER -> 3
    SeatSide.PASSENGER -> 2
}

/** The menu's height: whole rows, each on the 96dp floor. */
fun seatMenuHeight(side: SeatSide): Dp = Dimens.seatMenuRow * seatMenuRows(side)

/** The menu's left edge in bar coordinates. Driver left-aligns to its button; passenger right-aligns to its own. */
fun seatMenuX(side: SeatSide): Dp = when (side) {
    SeatSide.DRIVER -> Dimens.seatMenuDriverX
    SeatSide.PASSENGER -> Dimens.seatMenuPassengerX
}

/**
 * One seat's menu: seat heat, seat cool, and -- for the driver -- the heated
 * wheel. 360dp wide, 96dp rows, flush on top of the bar.
 *
 * Each tap **cycles**, exactly as the same control does on screen 1d: heat
 * goes `OFF -> HIGH -> LOW -> OFF` per tap, cool likewise, and the vehicle's
 * own mutual exclusion clears the other. There is no direct level selection
 * and no multi-command sequencing, by the owner's decision. Like everywhere
 * else this renders reported state only and never mutates on tap; the menu
 * stays open across row taps because reaching LOW takes two of them.
 *
 * Row rules follow the 1d comfort tiles: icon in the lit colour when on,
 * `ink` when off, `inkFaint` when unknown; state text in the lit colour when
 * on, muted when off, an em dash when unknown. The seat rows read "unknown"
 * off the level itself; the wheel row needs [ClimateState.hasClimateData]
 * because its flag collapses absent into false.
 *
 * The window this draws in is translucent over app content, so the menu
 * paints [Palette.surface] under its `surfaceRaised` wash: the wash alone is a
 * 5% overlay that would vanish over whatever app is behind it. The corners
 * are clipped so a row's pressed wash respects them.
 */
@Composable
fun SeatMenu(
    palette: Palette,
    side: SeatSide,
    state: ClimateState,
    onCommand: (Command) -> Unit,
    modifier: Modifier = Modifier,
) {
    val driver = side == SeatSide.DRIVER
    val live = state.hasClimateData
    val heat = if (driver) state.seatHeatL else state.seatHeatR
    val vent = if (driver) state.seatVentL else state.seatVentR
    val shape = RoundedCornerShape(Dimens.radiusMenu)

    Column(
        modifier
            .width(Dimens.seatMenuWidth)
            .height(seatMenuHeight(side))
            .background(palette.surface, shape)
            .background(palette.surfaceRaised, shape)
            .clip(shape),
    ) {
        MenuRow(
            palette = palette,
            icon = { tint ->
                if (driver) SeatHeatLeftGlyph(tint, Dimens.seatMenuIconSize)
                else SeatHeatRightGlyph(tint, Dimens.seatMenuIconSize)
            },
            label = "SEAT HEAT",
            stateText = levelText(heat),
            on = heat.isOn,
            known = heat != SeatLevel.UNAVAILABLE,
            litColor = palette.warm,
            divider = true,
            onClick = { onCommand(if (driver) Command.SEAT_HEAT_L else Command.SEAT_HEAT_R) },
        )
        MenuRow(
            palette = palette,
            icon = { tint ->
                if (driver) SeatCoolLeftGlyph(tint, Dimens.seatMenuIconSize)
                else SeatCoolRightGlyph(tint, Dimens.seatMenuIconSize)
            },
            label = "SEAT COOL",
            stateText = levelText(vent),
            on = vent.isOn,
            known = vent != SeatLevel.UNAVAILABLE,
            litColor = palette.cool,
            divider = driver,
            onClick = { onCommand(if (driver) Command.SEAT_VENT_L else Command.SEAT_VENT_R) },
        )
        if (driver) {
            MenuRow(
                palette = palette,
                icon = { tint -> WheelHeatGlyph(tint, Dimens.seatMenuIconSize) },
                label = "STEERING WHEEL",
                stateText = when {
                    !live -> "—"
                    state.wheelHeatOn -> "ON"
                    else -> "OFF"
                },
                on = live && state.wheelHeatOn,
                known = live,
                litColor = palette.warm,
                divider = false,
                onClick = { onCommand(Command.WHEEL_HEAT) },
            )
        }
    }
}

/**
 * One 96dp row: icon at 27dp, label at 74dp, state text right-aligned 24dp
 * from the edge. Pressed wash on touch-down, no ripple.
 */
@Composable
private fun MenuRow(
    palette: Palette,
    icon: @Composable (Color) -> Unit,
    label: String,
    stateText: String,
    on: Boolean,
    known: Boolean,
    litColor: Color,
    divider: Boolean,
    onClick: () -> Unit,
) {
    val (interaction, pressed) = rememberPressState()
    Row(
        Modifier
            .fillMaxWidth()
            .height(Dimens.seatMenuRow)
            .then(if (divider) Modifier.bottomDivider(palette.divider) else Modifier)
            .then(if (pressed.value) Modifier.pressedTint(filled = false, palette = palette) else Modifier)
            .target(interaction, onClick = onClick)
            .padding(start = Dimens.seatMenuIconInset, end = Dimens.seatMenuStateInset),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon(
            when {
                !known -> palette.inkFaint
                on -> litColor
                else -> palette.ink
            },
        )
        Spacer(Modifier.width(Dimens.seatMenuLabelX - Dimens.seatMenuIconInset - Dimens.seatMenuIconSize))
        BasicText(
            text = label,
            style = Type.menuLabel.copy(color = if (known) palette.ink else palette.inkMuted),
        )
        Spacer(Modifier.weight(1f))
        BasicText(
            text = stateText,
            style = Type.comfortState.copy(color = if (on) litColor else palette.inkFaint),
        )
    }
}

/** OFF / LOW / HIGH, and an em dash for UNAVAILABLE -- never `OFF` for a level we do not have. */
private fun levelText(level: SeatLevel): String = when (level) {
    SeatLevel.OFF -> "OFF"
    SeatLevel.LOW -> "LOW"
    SeatLevel.HIGH -> "HIGH"
    SeatLevel.UNAVAILABLE -> "—"
}

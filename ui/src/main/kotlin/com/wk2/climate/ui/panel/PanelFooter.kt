package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.HoldToConfirm
import com.wk2.climate.ui.pressedTint
import com.wk2.climate.ui.rememberPressState
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

@Composable
fun PanelFooter(
    palette: Palette,
    onPowerOff: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .topDivider(palette.divider)
            .padding(horizontal = Dimens.pageGutter, vertical = 22.dp),
        // End, not SpaceBetween: with the explanatory copy gone the button is
        // the row's only child, and SpaceBetween would park it at the left.
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HoldOffButton(palette, onPowerOff)
    }
}

/**
 * `HOLD \u00B7 OFF`.
 *
 * Fills with progress while held and fires only when the hold completes. A
 * single tap does nothing, and releasing early resets the fill to zero — the
 * progress is the affordance that tells the driver a hold is required. A press
 * wash lands on touch-down, before the fill is wide enough to see, because
 * `Interaction.kt`'s rule is feedback on touch-down and this is the control
 * where "did that register?" matters most.
 */
@Composable
private fun HoldOffButton(palette: Palette, onPowerOff: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val (interaction, pressed) = rememberPressState()
    val shape = RoundedCornerShape(Dimens.radiusPill)
    // pointerInput is keyed on Unit so a bus state update cannot restart the
    // gesture mid-hold and reset the elapsed-time baseline; the callback is
    // therefore read through a State so a completed hold dispatches to the
    // *current* onPowerOff rather than the first one this composable ever saw.
    val powerOff by rememberUpdatedState(onPowerOff)

    Box(
        Modifier
            .width(Dimens.holdOffWidth)
            .height(Dimens.minTarget)
            .clip(shape)
            .then(
                if (pressed.value) {
                    Modifier.pressedTint(filled = false, palette = palette, shape = shape)
                } else {
                    Modifier
                },
            )
            .border(Dimens.controlBorderWidth, palette.ink.copy(alpha = 0.18f), shape)
            .pointerInput(Unit) {
                // Structured exactly like Modifier.holdRepeatTarget in
                // ui/.../Interaction.kt -- read that first. The ticker is a
                // child of THIS coroutineScope, and cleanup lives in a
                // `finally`, so a cancellation arriving at a pointer-event
                // suspension point can neither skip the cleanup nor orphan the
                // ticker.
                //
                // That matters more here than anywhere else in the app: an
                // orphaned ticker would keep ticking and eventually call
                // onPowerOff(), which dispatches Command.CLIMATE_POWER -- the
                // one command that leaves this vehicle with no way to change
                // climate until it is sent again.
                coroutineScope {
                    val gestureScope = this
                    awaitEachGesture {
                        // requireUnconsumed = true, unlike holdRepeatTarget:
                        // Modifier.verticalScroll sits above this control and
                        // *consumes* the down that arrests a fling. Arming on a
                        // consumed down would let a driver who stops a
                        // scrolling page with a finger that lands on the pill,
                        // and leaves it there, power the climate off -- a
                        // stationary finger produces no further drag, so
                        // nothing would cancel it. holdRepeatTarget can afford
                        // `false` because its worst case is one temperature
                        // step; this control's worst case is the whole climate
                        // system.
                        val down = awaitFirstDown(requireUnconsumed = true)
                        val press = PressInteraction.Press(down.position)
                        interaction.tryEmit(press)

                        val ticker = gestureScope.launch {
                            // withFrameMillis, not the wall clock:
                            // this head unit sets its time from GPS and the
                            // network while the bar is live, and a forward
                            // wall-clock jump of >= POWER_HOLD_MS between the
                            // down and any tick would confirm what the driver
                            // performed as a tap. The frame clock is monotonic,
                            // and it also replaces a hand-rolled delay(16)
                            // poll with a tick locked to the display.
                            val start = withFrameMillis { it }
                            while (true) {
                                val held = withFrameMillis { it } - start
                                progress = HoldToConfirm.progressAt(held, Dimens.POWER_HOLD_MS)
                                if (HoldToConfirm.isConfirmed(held, Dimens.POWER_HOLD_MS)) {
                                    powerOff()
                                    return@launch
                                }
                            }
                        }
                        var releasedNormally = false
                        try {
                            // Not waitForUpOrCancellation(): that returns only
                            // once *every* pointer is up, so a palm or second
                            // finger resting inside the 180x96 pill would keep
                            // the hold alive after the driver lifted the finger
                            // they were holding with, and it would confirm at
                            // POWER_HOLD_MS having been let go of. The hold
                            // tracks the pointer that started it and requires
                            // that it be the only one down.
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.any { it.id != down.id && it.pressed }) {
                                    // A second pointer: no longer one
                                    // deliberate finger. Abort.
                                    break
                                }
                                val holding = event.changes.firstOrNull { it.id == down.id }
                                if (holding == null || !holding.pressed) {
                                    releasedNormally = true
                                    break
                                }
                                // An ancestor took the gesture over (a scroll
                                // won the drag), same as waitForUpOrCancellation
                                // returning null.
                                if (holding.isConsumed) break
                            }
                        } finally {
                            ticker.cancel()
                            // Reset on release, whether or not it fired.
                            progress = 0f
                            interaction.tryEmit(
                                if (releasedNormally) PressInteraction.Release(press)
                                else PressInteraction.Cancel(press),
                            )
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The fill, drawn behind the label and anchored to the leading edge so
        // it sweeps left-to-right rather than growing outward from under the
        // dot and label. Same reason RangeTrack in PanelZones.kt uses
        // CenterStart for its width-driven fill.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(progress)
                .background(palette.warm.copy(alpha = 0.35f)),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(16.dp)
                    .background(palette.ink.copy(alpha = 0.35f), CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            BasicText(
                text = "HOLD \u00B7 OFF",
                style = Type.holdOffLabel.copy(color = palette.ink.copy(alpha = 0.6f)),
            )
        }
    }
}

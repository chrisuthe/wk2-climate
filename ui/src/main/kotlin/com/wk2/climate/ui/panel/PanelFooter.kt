package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.HoldToConfirm
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = "This vehicle has no physical climate controls. Turning the " +
                "system off leaves no way to change temperature, fan or defrost " +
                "until it is turned back on, so this control needs a press and hold.",
            style = Type.footerCopy.copy(color = palette.inkFaint),
            modifier = Modifier.widthIn(max = 640.dp),
        )
        Spacer(Modifier.width(Dimens.pageGutter))
        HoldOffButton(palette, onPowerOff)
    }
}

/**
 * `HOLD · OFF`.
 *
 * Fills with progress while held and fires only when the hold completes. A
 * single tap does nothing, and releasing early resets the fill to zero — the
 * progress is the affordance that tells the driver a hold is required.
 */
@Composable
private fun HoldOffButton(palette: Palette, onPowerOff: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0f) }
    val shape = RoundedCornerShape(Dimens.radiusPill)

    Box(
        Modifier
            .width(Dimens.holdOffWidth)
            .height(Dimens.minTarget)
            .clip(shape)
            .border(Dimens.controlBorderWidth, palette.ink.copy(alpha = 0.18f), shape)
            .pointerInput(Unit) {
                // Structured exactly like Modifier.holdRepeatTarget in
                // ui/.../Interaction.kt -- read that first. The ticker is a
                // child of THIS coroutineScope, and cleanup lives in a
                // `finally`, so a cancellation arriving at the
                // waitForUpOrCancellation suspension point can neither skip the
                // cleanup nor orphan the ticker.
                //
                // That matters more here than anywhere else in the app: an
                // orphaned ticker would keep polling and eventually call
                // onPowerOff(), which dispatches Command.CLIMATE_POWER -- the
                // one command that leaves this vehicle with no way to change
                // climate until it is sent again.
                coroutineScope {
                    val gestureScope = this
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        val start = System.currentTimeMillis()
                        val ticker = gestureScope.launch {
                            var fired = false
                            while (isActive && !fired) {
                                val held = System.currentTimeMillis() - start
                                progress = HoldToConfirm.progressAt(held, Dimens.POWER_HOLD_MS)
                                if (HoldToConfirm.isConfirmed(held, Dimens.POWER_HOLD_MS)) {
                                    fired = true
                                    onPowerOff()
                                }
                                delay(16)
                            }
                        }
                        try {
                            waitForUpOrCancellation()
                        } finally {
                            ticker.cancel()
                            // Reset on release, whether or not it fired.
                            progress = 0f
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // The fill, drawn behind the label.
        Box(
            Modifier
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
                text = "HOLD · OFF",
                style = Type.holdOffLabel.copy(color = palette.ink.copy(alpha = 0.6f)),
            )
        }
    }
}

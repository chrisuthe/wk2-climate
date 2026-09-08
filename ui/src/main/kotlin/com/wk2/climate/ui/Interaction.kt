package com.wk2.climate.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

/**
 * Interaction primitives for a vehicle panel, which differs from a phone in
 * three ways the Compose defaults do not account for:
 *
 *  - **There is no hover.** Never implement hover styling.
 *  - **Feedback must appear on touch-down, not on release.** A driver glancing
 *    away needs to know the press registered before they lift.
 *  - **No ripple and no shadows.** Both read as smudges on a glossy panel in
 *    direct sunlight.
 *
 * So every target here passes `indication = null` and renders its own pressed
 * state from [MutableInteractionSource].
 */

/** Remembers an interaction source and its live pressed state together. */
@Composable
fun rememberPressState(): Pair<MutableInteractionSource, State<Boolean>> {
    val interaction = remember { MutableInteractionSource() }
    val pressed = interaction.collectIsPressedAsState()
    return interaction to pressed
}

/**
 * A single-shot tappable region. Deliberately no indication — the caller
 * renders the pressed state itself.
 */
@Composable
fun Modifier.target(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = clickable(
    interactionSource = interaction,
    indication = null,
    enabled = enabled,
    onClick = onClick,
)

/**
 * A tappable region that repeats while held, on [HoldRepeat]'s schedule.
 * Used by every `-` / `+`.
 */
@Composable
fun Modifier.holdRepeatTarget(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    onFire: () -> Unit,
): Modifier {
    val scope = rememberCoroutineScope()
    return pointerInput(enabled, onFire) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val press = PressInteraction.Press(down.position)
            scope.launch { interaction.emit(press) }

            val repeater = scope.launch { HoldRepeat.run(onFire) }

            // Stop repeating the instant the finger lifts or the gesture is
            // cancelled -- a target that kept firing after release would run
            // the temperature away from the driver.
            val up = waitForUpOrCancellation()
            repeater.cancel()
            scope.launch {
                interaction.emit(
                    if (up == null) PressInteraction.Cancel(press)
                    else PressInteraction.Release(press),
                )
            }
        }
    }
}

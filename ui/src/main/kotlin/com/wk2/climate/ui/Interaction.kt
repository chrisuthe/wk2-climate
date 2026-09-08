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
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.coroutineScope
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
 *
 * The `coroutineScope` wraps [awaitEachGesture] rather than using
 * `rememberCoroutineScope()`, so `repeater` is a *structured child* of the
 * currently running `pointerInput` coroutine, not of the composable's whole
 * lifetime. `pointerInput` restarts this coroutine whenever [onFire]'s
 * identity changes -- which happens on every recomposition a caller like
 * `onCommand(Command.TEMP_L_UP)` causes -- and cancelling it now cancels
 * `repeater` with it via the job hierarchy, regardless of where `repeater`
 * happens to be suspended (typically mid-[HoldRepeat.INTERVAL_MS] delay).
 * There is no longer-lived scope for it to be orphaned on.
 *
 * `AwaitPointerEventScope` (the receiver inside [awaitEachGesture]) is
 * `@RestrictsSuspension`: only its own member/extension suspend functions
 * ([awaitFirstDown], [waitForUpOrCancellation]) may be called there, so
 * cleanup uses non-suspending calls only -- [kotlinx.coroutines.Job.cancel]
 * and [MutableInteractionSource.tryEmit] -- both in a `finally`, so the
 * Release/Cancel interaction is always emitted, even when this coroutine is
 * cancelled out from under `waitForUpOrCancellation()`. Without that, the
 * pressed state could stick "on" after a restart.
 */
@Composable
fun Modifier.holdRepeatTarget(
    interaction: MutableInteractionSource,
    enabled: Boolean = true,
    onFire: () -> Unit,
): Modifier = pointerInput(enabled, onFire) {
    if (!enabled) return@pointerInput
    coroutineScope {
        val gestureScope = this
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val press = PressInteraction.Press(down.position)
            interaction.tryEmit(press)

            val repeater = gestureScope.launch { HoldRepeat.run(onFire) }
            var releasedNormally = false
            try {
                releasedNormally = waitForUpOrCancellation() != null
            } finally {
                repeater.cancel()
                interaction.tryEmit(
                    if (releasedNormally) PressInteraction.Release(press)
                    else PressInteraction.Cancel(press),
                )
            }
        }
    }
}

package com.wk2.climate.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlin.coroutines.coroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression coverage for the leaked-repeater bug in
 * [Modifier.holdRepeatTarget][com.wk2.climate.ui.holdRepeatTarget].
 *
 * `holdRepeatTarget`'s body cannot be called from a plain JVM test: it needs
 * a real `PointerInputScope`/`AwaitPointerEventScope`, which only exists
 * inside a live composition (`ComposeTestRule` or Robolectric), and neither
 * is a dependency of this module. `AwaitPointerEventScope` is also
 * `@RestrictsSuspension`, which is precisely why the fix could not be
 * factored into a separately-callable suspend function either -- inside
 * `awaitEachGesture`, only `AwaitPointerEventScope`'s own member/extension
 * suspend functions may be called, so the gesture body has to stay inlined.
 *
 * What *is* testable on the JVM, and what these tests pin, is the structural
 * concurrency principle the fix depends on: a repeater launched as a child
 * of the `coroutineScope` that wraps `awaitEachGesture` is cancelled the
 * instant that scope's job is cancelled -- which is what happens when
 * `pointerInput` restarts on a fresh `onFire` identity mid-hold -- even
 * while suspended mid-delay, with no code path required to reach it. The
 * first test below reproduces the *old*, buggy wiring (a repeater launched
 * on a scope unrelated to the job that gets cancelled, standing in for
 * `rememberCoroutineScope()`) and shows it does **not** stop; the second
 * reproduces the *fixed* wiring and shows it does.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class InteractionTest {

    @Test
    fun `pre-fix wiring - a repeater on an unrelated scope survives the gesture job being cancelled`() = runTest {
        var fired = 0
        // Stands in for `rememberCoroutineScope()`: a scope that lives as
        // long as the composition, structurally unrelated to any one
        // `pointerInput` invocation. `+ Job()` gives it its own job (rather
        // than reusing the test body's) while keeping the test dispatcher,
        // so virtual time still applies to it but cancelling it below
        // cannot cancel the test itself.
        val compositionScope = CoroutineScope(coroutineContext + Job())
        val gestureJob = launch {
            // Stands in for the old holdRepeatTarget: repeater launched on
            // the unrelated scope, not as this coroutine's child.
            compositionScope.launch { HoldRepeat.run { fired++ } }
            awaitCancellation() // stands in for waitForUpOrCancellation()
        }

        advanceTimeBy(HoldRepeat.INITIAL_DELAY_MS + HoldRepeat.INTERVAL_MS * 2 + 1)
        val firedAtCancel = fired

        gestureJob.cancel() // stands in for pointerInput restarting mid-hold
        advanceTimeBy(HoldRepeat.INTERVAL_MS * 4)

        assertTrue(
            "documents the bug: an unrelated-scope repeater keeps firing after " +
                "the gesture job is cancelled",
            fired > firedAtCancel,
        )
        compositionScope.cancel()
    }

    @Test
    fun `fixed wiring - a repeater launched as a structured child is stopped by the parent job, even mid-delay`() = runTest {
        var fired = 0
        val job = launch {
            coroutineScope { // stands in for the coroutineScope wrapping awaitEachGesture
                launch { HoldRepeat.run { fired++ } } // repeater: a child of this scope
                awaitCancellation() // stands in for waitForUpOrCancellation()
            }
        }

        // Let it fire a few times, landing mid-delay, not at a cleanup point.
        advanceTimeBy(HoldRepeat.INITIAL_DELAY_MS + HoldRepeat.INTERVAL_MS * 2 + 1)
        val firedAtCancel = fired
        assertTrue("expected repeats before cancelling", firedAtCancel >= 3)

        job.cancel() // stands in for pointerInput restarting mid-hold
        advanceTimeBy(10_000)

        assertEquals(
            "a structured child is cancelled with its parent even mid-delay",
            firedAtCancel,
            fired,
        )
    }
}

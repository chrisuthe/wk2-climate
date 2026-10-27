package com.wk2.climate.bus

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow

/**
 * Whether the system navigation bar is on screen, as a flow, for a bar that
 * has to step aside when an app hides it.
 *
 * A poll rather than a callback because there is no callback to be had. The
 * bar is a `TYPE_ACCESSIBILITY_OVERLAY`, which sits above the navigation bar,
 * and since Android 12 a window only receives insets from sources *above* it:
 * measured on the vehicle, our window was told `navigationBars` was invisible
 * with zero insets while the factory bar was plainly on screen, and was never
 * told anything again. The display's own insets state does change, so that is
 * what [isShown] reads.
 *
 * Kept free of Android, like [ConnectionGate], so the timing is provable with
 * virtual time; `ClimateBarService` owns what [isShown] reads.
 */
object NavBarWatch {

    /**
     * Emits [isShown] at once, then re-reads it every [periodMs] and emits
     * only when it changes, so a hide is seen within one period.
     */
    fun follow(periodMs: Long, isShown: () -> Boolean): Flow<Boolean> = flow {
        while (true) {
            emit(isShown())
            delay(periodMs)
        }
    }.distinctUntilChanged()
}

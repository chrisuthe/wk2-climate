package com.wk2.climate.ui

import com.wk2.climate.design.Dimens
import kotlinx.coroutines.delay

/**
 * The press-and-hold repeat schedule for `−` / `+` targets.
 *
 * Fires immediately on touch-down, waits [INITIAL_DELAY_MS], then repeats every
 * [INTERVAL_MS] until cancelled. The immediate first fire is what makes a
 * single tap feel instant; the delay before repeating is what stops a tap from
 * accidentally stepping twice.
 *
 * Kept as a suspend function with no Compose dependency so the timing is
 * verified with virtual time instead of by holding a finger on a screen.
 */
object HoldRepeat {
    // Aliases, not copies: the schedule is a design token, so `:design` owns
    // the numbers and this is only a local name for them.
    const val INITIAL_DELAY_MS = Dimens.HOLD_REPEAT_DELAY_MS
    const val INTERVAL_MS = Dimens.HOLD_REPEAT_INTERVAL_MS

    suspend fun run(onFire: () -> Unit) {
        onFire()
        delay(INITIAL_DELAY_MS)
        while (true) {
            onFire()
            delay(INTERVAL_MS)
        }
    }
}

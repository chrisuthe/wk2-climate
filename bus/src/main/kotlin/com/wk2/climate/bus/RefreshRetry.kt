package com.wk2.climate.bus

import kotlinx.coroutines.delay

/**
 * The observable behaviour behind nagging the vendor service into reporting
 * climate state after a cold start.
 *
 * Kept as a suspend function with no Android dependency, for the same reason
 * [ConnectionGate] is: the only place this ever runs for real is a vehicle
 * whose ignition has just been cycled, which is not a state an emulator can
 * produce. `ClimateBarService` owns *why* the bar wants climate data and what
 * a refresh does to the Binder; this owns *how often* it asks and when it
 * gives up.
 */
object RefreshRetry {

    /**
     * Delay **before each attempt**, so attempts land at roughly 1s, 2s, 4s
     * and 8s after connect, and the last one is the last.
     *
     * The shape is a doubling backoff with a hard end, chosen against what was
     * measured on the vehicle:
     *
     *  - **1s first.** `bindService` plus 20 registrations complete in far less
     *    than that, so by 1s the subscribe-only path has demonstrably produced
     *    nothing and a re-register is worth trying. Asking sooner would race
     *    our own registration.
     *  - **Doubling.** If the MCU is reporting no climate frames at all —
     *    which is what `seeded 0/20` with the ignition off means — then no
     *    number of re-registrations will help, and hammering a vendor service
     *    that is answering correctly is the wrong response to our own
     *    ignorance. Backing off keeps the useful early attempts dense while
     *    the pointless later ones are cheap.
     *  - **Stopping at ~8s.** Past that the driver has either seen the bar
     *    populate or is already reaching for a control, and their first `+`
     *    press is a genuine change that the subscription will deliver anyway.
     *    An unbounded retry would sit in the log ring buffer — 256 KiB on this
     *    unit, wrapping in well under a minute — evicting everything else for
     *    the rest of the drive.
     *
     * Four attempts, ~8s total. If climate data has not arrived by then the
     * bar honestly shows that it does not know, which is Part B's job.
     */
    val DEFAULT_DELAYS_MS: List<Long> = listOf(1_000L, 1_000L, 2_000L, 4_000L)

    /**
     * Waits [delaysMs] entry, checks [hasData], calls [refresh] — repeated for
     * each entry, then returns for good.
     *
     * Returns immediately if [hasData] is already true, and returns as soon as
     * it becomes true, so a bus that answered the first attempt costs nothing
     * further. [refresh] is never called after data has arrived.
     *
     * Bounded by construction: there is no loop that outlives [delaysMs]. The
     * caller's coroutine is still the owner — cancelling the scope cancels the
     * [delay] and stops this mid-schedule.
     */
    suspend fun refreshUntilData(
        hasData: () -> Boolean,
        refresh: () -> Unit,
        delaysMs: List<Long> = DEFAULT_DELAYS_MS,
    ) {
        for (waitMs in delaysMs) {
            if (hasData()) return
            delay(waitMs)
            if (hasData()) return
            refresh()
        }
    }
}

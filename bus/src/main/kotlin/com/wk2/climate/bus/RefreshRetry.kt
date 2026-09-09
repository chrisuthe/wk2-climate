package com.wk2.climate.bus

import kotlinx.coroutines.delay

/**
 * The observable behaviour behind nagging the vendor service into reporting
 * the signals we registered for but have never been told a value for.
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
     *  - **Doubling.** If the vehicle is not reporting a signal at all —
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
     * Four attempts, ~8s total. Whatever is still missing after that is
     * logged and left alone: the bar renders honestly from what it has, and
     * an unknown illumination selects the night palette.
     */
    val DEFAULT_DELAYS_MS: List<Long> = listOf(1_000L, 1_000L, 2_000L, 4_000L)

    /**
     * Waits a [delaysMs] entry, asks [missing], calls [refresh] — repeated for
     * each entry, then settles for good.
     *
     * The completion condition is **nothing expected is still missing**, not
     * "some data arrived". Gating on the latter is what let the reported
     * defect through: climate frames stream continuously, so the retry
     * completed on its first check while `VOLUME` and `ILLUMINATION` — signals
     * that are static until someone acts — had never been pushed at all, and
     * were never asked for again.
     *
     * Returns immediately if [missing] is already empty, and returns as soon
     * as it becomes empty, so a bus that answered costs nothing further.
     * [refresh] is never called once nothing is missing.
     *
     * [onAttempt] is called once per attempt, before [refresh], with the set
     * that is still missing at that moment; [onSettled] is called exactly once
     * on the way out with the final set — empty or not. Both are the caller's
     * logging: keeping them as callbacks is what keeps this file Android-free
     * and its timing provable with virtual time.
     *
     * A non-empty set at [onSettled] is a normal outcome. Some of these
     * signals may not exist on this vehicle at all, and no number of
     * re-registrations will conjure one; this is deliberately not escalated
     * past a fact the caller can log.
     *
     * Bounded by construction: there is no loop that outlives [delaysMs]. The
     * caller's coroutine is still the owner — cancelling the scope cancels the
     * [delay] and stops this mid-schedule.
     */
    suspend fun refreshUntilNothingMissing(
        missing: () -> Set<Signal>,
        refresh: () -> Unit,
        onAttempt: (attempt: Int, total: Int, missing: Set<Signal>) -> Unit = { _, _, _ -> },
        onSettled: (attempts: Int, missing: Set<Signal>) -> Unit = { _, _ -> },
        delaysMs: List<Long> = DEFAULT_DELAYS_MS,
    ) {
        var attempt = 0
        for (waitMs in delaysMs) {
            if (missing().isEmpty()) break
            delay(waitMs)
            val stillMissing = missing()
            if (stillMissing.isEmpty()) break
            attempt++
            onAttempt(attempt, delaysMs.size, stillMissing)
            refresh()
        }
        onSettled(attempt, missing())
    }
}

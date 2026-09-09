package com.wk2.climate.bus

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The observable behaviour behind showing and hiding an overlay as a
 * [VehicleBus]'s connection comes and goes.
 *
 * Kept as a suspend function with no Android dependency so the timing is
 * provable with virtual time instead of on an emulator with no vendor bus to
 * disconnect. `ClimateBarService.followBus()` owns *why* the bar is tied to
 * the connection and the window it shows or hides; this owns *how* the
 * following behaves.
 */
object ConnectionGate {

    /**
     * Calls [show] immediately. If [connected] is not already `true`, gives it
     * up to [graceMs] to report a first `true` before falling through, so an
     * ordinarily slow connection does not flash [hide] into view. From then on
     * — or immediately, if already connected — it simply follows [connected]
     * for as long as the calling coroutine runs: `true` calls [show], `false`
     * calls [hide], with no further grace period. A later disconnect hides
     * with no delay, and a later reconnect shows again.
     *
     * [connected] must replay its current value to a new subscriber — a
     * [kotlinx.coroutines.flow.StateFlow] does this by construction — because
     * this reads that current value via [Flow.first] before collecting the
     * same flow again for the follow-forever step below. That peek does not
     * cost the second subscription anything on a conflated, replaying flow:
     * every subscriber receives the latest value independently, however many
     * there are. A cold or non-replaying flow does not have that property and
     * must not be passed here.
     */
    suspend fun followConnection(
        connected: Flow<Boolean>,
        graceMs: Long,
        show: () -> Unit,
        hide: () -> Unit,
    ) {
        show()
        if (!connected.first()) {
            withTimeoutOrNull(graceMs) { connected.first { it } }
        }
        connected.collect { isConnected -> if (isConnected) show() else hide() }
    }
}

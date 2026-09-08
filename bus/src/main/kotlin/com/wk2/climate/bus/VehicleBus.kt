package com.wk2.climate.bus

import kotlinx.coroutines.flow.StateFlow

/**
 * The seam between the UI and the vehicle.
 *
 * State is owned by the vehicle, not by us. A tap dispatches a [Command] and
 * mutates nothing locally; the UI updates when the vehicle reports the new
 * value through [state]. That single rule is what makes the protocol's
 * side effects — mutual exclusion, macro fallout, AUTO clearing itself when
 * the fan moves — appear correctly with no special-casing.
 */
interface VehicleBus {

    /** The full snapshot. Seeded on subscription, then updated on change. */
    val state: StateFlow<ClimateState>

    /** Whether we currently hold a live connection to the vendor service. */
    val connected: StateFlow<Boolean>

    /** Dispatch a write. Never update [state] optimistically in response. */
    fun send(command: Command)
}

package com.wk2.climate.ui

/**
 * Hold-to-confirm arithmetic for the one destructive control in the app.
 *
 * Kept separate and pure because the property that matters — **a tap can never
 * confirm** — is worth asserting in a test rather than trusting to gesture
 * plumbing. This vehicle has no physical HVAC controls, so an accidental
 * power-off leaves no way to change anything until it is sent again.
 */
object HoldToConfirm {

    /** Fraction of the required hold elapsed, clamped to 0..1. */
    fun progressAt(heldMs: Long, requiredMs: Long): Float {
        if (requiredMs <= 0L || heldMs <= 0L) return 0f
        return (heldMs.toFloat() / requiredMs.toFloat()).coerceIn(0f, 1f)
    }

    /**
     * True only once the full hold has elapsed. A non-positive requirement
     * never confirms — a misconfiguration must fail closed, not turn the
     * destructive control into a single tap.
     */
    fun isConfirmed(heldMs: Long, requiredMs: Long): Boolean =
        requiredMs > 0L && heldMs >= requiredMs
}

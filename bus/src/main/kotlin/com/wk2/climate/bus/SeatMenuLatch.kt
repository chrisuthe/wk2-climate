package com.wk2.climate.bus

/** Which seat a button, a menu or a command belongs to. Driver is the left seat on this vehicle. */
enum class SeatSide { DRIVER, PASSENGER }

/**
 * Decides which seat menu, if any, is open.
 *
 * The menu lives in its own overlay window above the bar, opened by a tap on
 * its seat button and closed by a touch anywhere outside it. Those two facts
 * collide on one gesture: **tapping the owning seat button while its menu is
 * open is also a touch outside the menu window.** The window sees the touch's
 * DOWN as `ACTION_OUTSIDE` and closes; the bar then sees the same touch's UP
 * as a click on the seat button and, with nothing open, would reopen it. The
 * driver taps once and the menu flickers shut and open again.
 *
 * The rule here: an outside touch closes the menu and records which side it
 * closed and when. A click on *that* side's button inside [retapWindowMillis]
 * is the UP of the same tap, so it is swallowed. A click on the other side, or
 * on the same side after the window, is a fresh tap and opens. The record is
 * consumed by the first click that consults it, so a quick second tap after a
 * swallowed one opens as the driver expects.
 *
 * 400 ms is the platform's own long-press timeout: a touch released inside it
 * is a tap by Android's definition, and one held past it is not. The outside
 * touch is timestamped at DOWN and the click at UP, so the window has to cover
 * a whole tap.
 *
 * Pure Kotlin with an injected clock, alongside [ConnectionGate] and
 * [RefreshRetry], so the race is proven here rather than found in the car.
 * Checking `ACTION_OUTSIDE` coordinates against the button rect would be the
 * other way to do this; those coordinates are only populated for touches on
 * windows of the same UID, which happens to be true here, but a rule that
 * does not depend on it is simpler to reason about.
 *
 * Every mutator returns the new [open] so the caller can apply it in one
 * expression. Row taps inside the menu never come here: the menu stays open
 * across them, because cycling a seat to LOW takes two taps.
 */
class SeatMenuLatch(private val retapWindowMillis: Long = DEFAULT_RETAP_WINDOW_MS) {

    var open: SeatSide? = null
        private set

    private var outsideClosed: SeatSide? = null
    private var outsideClosedAt = Long.MIN_VALUE / 4

    /** A click on a seat button, from the bar. */
    fun onButtonTap(side: SeatSide, nowMillis: Long): SeatSide? {
        val sameTapAsTheOutsideTouch =
            open == null && side == outsideClosed && nowMillis - outsideClosedAt < retapWindowMillis
        outsideClosed = null
        open = when {
            sameTapAsTheOutsideTouch -> null
            open == side -> null
            else -> side
        }
        return open
    }

    /** `ACTION_OUTSIDE` on the open menu's window. */
    fun onOutsideTouch(nowMillis: Long): SeatSide? {
        val was = open ?: return null
        open = null
        outsideClosed = was
        outsideClosedAt = nowMillis
        return null
    }

    /** Every non-touch close: BACK, HOME, CLIMATE, a dead bus, teardown. Leaves no re-tap record. */
    fun close(): SeatSide? {
        open = null
        outsideClosed = null
        return null
    }

    companion object {
        const val DEFAULT_RETAP_WINDOW_MS = 400L
    }
}

package com.wk2.climate.bus

/** What the one variable region of screen 2a is currently holding. */
enum class SlotContent { FRONT_DEFROST, SEAT_HEAT, SEAT_COOL }

/**
 * Decides the contents of screen 2a's single adaptive slot.
 *
 * Screen 2a has fixed geometry so controls can be found by feel. Exactly one
 * 200x96 region ever changes what it holds, and this decides when. Three rules
 * matter more than the thresholds:
 *
 *  - **Hysteresis.** A vehicle sitting at the boundary must not swap the
 *    control repeatedly. A band must be cleared by [deadbandF] to be left.
 *  - **Dwell.** At most one change per [dwellMillis].
 *  - **Touch safety.** Never change under a finger, and never within
 *    [tapLockoutMillis] of a tap — otherwise the driver's next press lands on
 *    something they did not aim at.
 *
 * Pure Kotlin with an injected clock, so all of it is unit-tested against a
 * synthetic temperature series rather than by sitting in a cold car.
 *
 * A null outside temperature pins to [SlotContent.SEAT_HEAT], the middle band,
 * which is always safe. `U_TEMP_OUT` **is** decoded (spec section 5.3), so this
 * is no longer the shipping path — it is the fallback for the cases that remain:
 * the validity bit clear, the signal not yet arrived, or the vehicle reporting
 * a unit we do not trust the decode against. The slot must never be blank nor
 * change the bar's geometry.
 */
class AdaptiveSlot(
    private val deadbandF: Int = 3,
    private val dwellMillis: Long = 30_000L,
    private val tapLockoutMillis: Long = 1_000L,
    initial: SlotContent = SlotContent.SEAT_HEAT,
) {
    var content: SlotContent = initial
        private set

    private var lastChangeAt = Long.MIN_VALUE / 4
    private var lockedUntil = Long.MIN_VALUE / 4
    private var fingerDown = false

    fun onFingerDown() { fingerDown = true }

    fun onFingerUp() { fingerDown = false }

    fun onTap(nowMillis: Long) {
        lockedUntil = nowMillis + tapLockoutMillis
    }

    /**
     * Re-evaluate against the current outside temperature and return what the
     * slot should hold. Safe to call as often as you like.
     */
    fun update(outsideF: Int?, nowMillis: Long): SlotContent {
        val target = if (outsideF == null) SlotContent.SEAT_HEAT else targetFor(outsideF)

        if (target == content) return content
        if (fingerDown) return content
        if (nowMillis < lockedUntil) return content
        if (nowMillis - lastChangeAt < dwellMillis) return content

        content = target
        lastChangeAt = nowMillis
        return content
    }

    /**
     * The band for a temperature, biased to keep the current content.
     *
     * The deadband is applied **on exit only**. Entering a band happens at its
     * nominal threshold (32 F, 85 F); leaving it requires clearing that
     * threshold by [deadbandF]. Applying the band on entry as well would push
     * the effective thresholds to 29 F and 88 F, which is not what the design
     * specifies.
     *
     * Defrost outranks comfort: below freezing, clearing glass wins.
     */
    private fun targetFor(t: Int): SlotContent = when (content) {
        // Committed to defrost — must clear 35 F to leave.
        SlotContent.FRONT_DEFROST ->
            if (t >= FREEZING + deadbandF) bandOf(t) else SlotContent.FRONT_DEFROST

        // Committed to cooling — must fall to 82 F to leave.
        SlotContent.SEAT_COOL ->
            if (t <= HOT - deadbandF) bandOf(t) else SlotContent.SEAT_COOL

        // Neutral — enter either extreme at its nominal threshold.
        SlotContent.SEAT_HEAT -> bandOf(t)
    }

    private fun bandOf(t: Int): SlotContent = when {
        t < FREEZING -> SlotContent.FRONT_DEFROST
        t > HOT -> SlotContent.SEAT_COOL
        else -> SlotContent.SEAT_HEAT
    }

    companion object {
        const val FREEZING = 32
        const val HOT = 85
    }
}

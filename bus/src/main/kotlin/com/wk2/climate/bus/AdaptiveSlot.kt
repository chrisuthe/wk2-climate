package com.wk2.climate.bus

/** What one of screen 2a's two adaptive cells is currently holding. */
enum class SlotContent { FRONT_DEFROST, SEAT_HEAT, SEAT_COOL, WHEEL, MAX_AC }

/** Which of the two adaptive cells an event came from. */
enum class SlotCell { FIRST, SECOND }

/**
 * An outside-temperature band, and the pair of contents it puts in screen 2a's
 * two adaptive cells.
 *
 * The band **is** the pair. One value carries both cells, so a mismatched
 * pair -- FRONT DEFROST beside MAX A/C, say -- is not representable at all,
 * and no mid-transition state can produce one. That is the reason [AdaptiveSlot]
 * is one state machine rather than two independent ones: the cells flip
 * together, off the same band, or not at all.
 *
 * The pairings are the owner's, decided explicitly:
 *
 * | outside            | first cell     | second cell |
 * |--------------------|----------------|-------------|
 * | below 32 F         | FRONT DEFROST  | SEAT HEAT   |
 * | 32-85 F            | WHEEL          | SEAT HEAT   |
 * | above 85 F         | SEAT COOL      | MAX A/C     |
 */
enum class SlotBand(val first: SlotContent, val second: SlotContent) {
    COLD(SlotContent.FRONT_DEFROST, SlotContent.SEAT_HEAT),
    MILD(SlotContent.WHEEL, SlotContent.SEAT_HEAT),
    HOT(SlotContent.SEAT_COOL, SlotContent.MAX_AC),
}

/**
 * Decides the contents of screen 2a's two adaptive cells.
 *
 * Screen 2a has fixed geometry so controls can be found by feel. AUTO, CLIMATE
 * and both side columns never move and never change what they hold; the two
 * middle cells are the one variable region, and they change **together** as a
 * [SlotBand]. This decides when. Three rules matter more than the thresholds:
 *
 *  - **Hysteresis.** A vehicle sitting at the boundary must not swap the
 *    controls repeatedly. A band must be cleared by [deadbandF] to be left.
 *  - **Dwell.** At most one change per [dwellMillis].
 *  - **Touch safety.** Never change under a finger -- on *either* cell, since
 *    both move at once -- and never within [tapLockoutMillis] of a tap on
 *    either, otherwise the driver's next press lands on something they did not
 *    aim at.
 *
 * Pure Kotlin with an injected clock, so all of it is unit-tested against a
 * synthetic temperature series rather than by sitting in a cold car.
 *
 * A null outside temperature pins to [SlotBand.MILD], the middle band, which
 * is always safe. `U_TEMP_OUT` **is** decoded (spec section 5.3), so this is
 * no longer the shipping path -- it is the fallback for the cases that remain:
 * the validity bit clear, the signal not yet arrived, or the vehicle reporting
 * a unit we do not trust the decode against. Neither cell must ever be blank
 * nor change the bar's geometry.
 */
class AdaptiveSlot(
    private val deadbandF: Int = 3,
    private val dwellMillis: Long = 30_000L,
    private val tapLockoutMillis: Long = 1_000L,
    initial: SlotBand = SlotBand.MILD,
) {
    var band: SlotBand = initial
        private set

    private var lastChangeAt = Long.MIN_VALUE / 4
    private var lockedUntil = Long.MIN_VALUE / 4

    /**
     * The cells currently under a finger.
     *
     * A set rather than a counter or a single flag: there are two cells now,
     * each with its own press state, and a plain boolean would let cell A's
     * release clear the freeze that cell B's still-held finger is relying on.
     * Set semantics also make a duplicated or dropped down/up idempotent
     * rather than leaving the machine frozen forever.
     */
    private val fingersDown = mutableSetOf<SlotCell>()

    fun onFingerDown(cell: SlotCell) { fingersDown.add(cell) }

    fun onFingerUp(cell: SlotCell) { fingersDown.remove(cell) }

    /**
     * Arm the lockout after a tap. Not per-cell: a tap on either cell must
     * freeze the whole pair, because a swap moves both.
     */
    fun onTap(nowMillis: Long) {
        lockedUntil = nowMillis + tapLockoutMillis
    }

    /**
     * Re-evaluate against the current outside temperature and return the pair
     * both cells should hold. Safe to call as often as you like.
     */
    fun update(outsideF: Int?, nowMillis: Long): SlotBand {
        val target = if (outsideF == null) SlotBand.MILD else targetFor(outsideF)

        if (target == band) return band
        if (fingersDown.isNotEmpty()) return band
        if (nowMillis < lockedUntil) return band
        if (nowMillis - lastChangeAt < dwellMillis) return band

        band = target
        lastChangeAt = nowMillis
        return band
    }

    /**
     * The band for a temperature, biased to keep the current band.
     *
     * The deadband is applied **on exit only**. Entering a band happens at its
     * nominal threshold (32 F, 85 F); leaving it requires clearing that
     * threshold by [deadbandF]. Applying the band on entry as well would push
     * the effective thresholds to 29 F and 88 F, which is not what the design
     * specifies.
     *
     * Defrost outranks comfort: below freezing, clearing glass wins.
     */
    private fun targetFor(t: Int): SlotBand = when (band) {
        // Committed to defrost -- must clear 35 F to leave.
        SlotBand.COLD -> if (t >= FREEZING + deadbandF) bandOf(t) else SlotBand.COLD

        // Committed to cooling -- must fall to 82 F to leave.
        SlotBand.HOT -> if (t <= HOT - deadbandF) bandOf(t) else SlotBand.HOT

        // Neutral -- enter either extreme at its nominal threshold.
        SlotBand.MILD -> bandOf(t)
    }

    private fun bandOf(t: Int): SlotBand = when {
        t < FREEZING -> SlotBand.COLD
        t > HOT -> SlotBand.HOT
        else -> SlotBand.MILD
    }

    companion object {
        const val FREEZING = 32
        const val HOT = 85
    }
}

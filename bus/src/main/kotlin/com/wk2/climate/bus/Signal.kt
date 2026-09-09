package com.wk2.climate.bus

/**
 * A **read** id on the vendor bus — what you subscribe to and receive through
 * the callback. Codes are universal across FYT UIS7870 units.
 *
 * These are NOT the values you write. Sending a read id to the vendor `cmd()`
 * is accepted and silently discarded, which looks exactly like a permission
 * failure and is not one. Writes are [Command].
 *
 * A code is unique only *within* a module: code 2 is `U_STANDBY` on MAIN and
 * `U_VOL` on SOUND, which is why every entry carries its module.
 */
enum class Signal(
    val module: Int,
    val code: Int,
    /**
     * True for a signal we register out of interest rather than need.
     *
     * It is excluded from [ClimateState.missingSignals], which is the stopping
     * rule for the re-registration retry. A diagnostic that this vehicle never
     * reports would otherwise keep that retry running to its cap on every
     * start, for a value nothing renders.
     */
    val diagnostic: Boolean = false,
) {

    // ---- module 7: CANBUS (climate) ----
    POWER(7, 10),
    AC(7, 11),
    RECIRC(7, 12),
    AUTO(7, 13),
    REAR_DEFROST(7, 16),
    BLOW_UP(7, 18),
    BLOW_BODY(7, 19),
    BLOW_FOOT(7, 20),
    WIND_LEVEL(7, 21),
    TEMP_LEFT(7, 27),
    TEMP_RIGHT(7, 28),
    SEAT_HEAT_L(7, 29),
    SEAT_HEAT_R(7, 30),
    SEAT_VENT_L(7, 31),
    SEAT_VENT_R(7, 32),
    TEMP_UNIT(7, 37),
    AC_MAX(7, 53),
    /**
     * The vendor's DUAL flag: **1 = zones independent, 0 = zones synced**
     * (measured). `ClimateState.syncOn` inverts it, because the UI presents
     * SYNC rather than DUAL.
     */
    SYNC(7, 62),
    FRONT_DEFROST(7, 65),
    WHEEL_HEAT(7, 66),

    /**
     * The canbus id: which vehicle the unit is configured for.
     *
     * Read-only, and nothing depends on it yet. It is registered so we can find
     * out whether it actually arrives, and when - the question that decides
     * whether per-vehicle auto-configuration is possible at all. See
     * `docs/captures/2026-09-09-air-profile-extraction.md`.
     *
     * `com.syu.air` reads exactly this code and passes the value straight to
     * `AirFactory.create`, which switches it onto one of 222 vehicle profile
     * classes. It never queries the code either; it waits for the callback,
     * which is consistent with registration being the only read path.
     *
     * Absence is a real possibility and is handled: this contributes to
     * [ClimateState.missingSignals] but gates nothing, so a vehicle that never
     * reports it behaves exactly as before. [CanbusId] decodes it.
     */
    CANBUS_ID(7, 1000, diagnostic = true),

    // ---- module 4: SOUND ----
    VOLUME(4, 2),

    // ---- module 0: MAIN ----
    /**
     * Outside temperature, **decoded** — a packed word carrying tenths of a
     * degree offset by 1000 in the low 16 bits, with bit 28 as a validity
     * flag. Verified on the vehicle against the head unit's own status bar:
     * `0x10000744` -> 1860 -> 86 F. See the spec, section 5.3.
     *
     * The stationary session read it as a static configuration word only
     * because ambient barely moves in a parked car; a driving capture settled
     * it. The decode lives in `ClimateBarService.outsideF`, which fails safe
     * to null when the validity bit is clear.
     */
    TEMP_OUT(0, 40),

    /** Illumination / headlights. Drives the day/night theme, not a clock. */
    ILLUMINATION(0, 4),
    ;

    companion object {
        /**
         * Module 7 — CANBUS. Every climate command and every climate signal
         * goes through it; nothing else does. [SyuVehicleBus.connected] is
         * gated on this module alone, not on whether *something* bound.
         */
        const val MODULE_CANBUS = 7

        private val byModuleCode: Map<Long, Signal> =
            entries.associateBy { key(it.module, it.code) }

        private fun key(module: Int, code: Int): Long =
            (module.toLong() shl 32) or (code.toLong() and 0xFFFFFFFFL)

        fun of(module: Int, code: Int): Signal? = byModuleCode[key(module, code)]

        /** The modules we must bind and register one callback each against. */
        fun modules(): Set<Int> = entries.mapTo(LinkedHashSet()) { it.module }

        fun inModule(module: Int): List<Signal> = entries.filter { it.module == module }
    }
}

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
enum class Signal(val module: Int, val code: Int) {

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
    SYNC(7, 62),
    FRONT_DEFROST(7, 65),
    WHEEL_HEAT(7, 66),

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

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

    // The rear zone: the codes FYT's own REAR tab reads
    // (docs/captures/2026-10-28-pa-rear-climate.md). Registered on every car;
    // only a car whose driver said it has rear climate draws them.
    /** Rear setpoint; the front's sentinels and [TEMP_UNIT] apply. */
    REAR_TEMP(7, 40),
    REAR_POWER(7, 42),
    REAR_AUTO(7, 43),
    /** Rear fan, 0..7. */
    REAR_FAN(7, 44),
    /** Rear airflow to the face. With [REAR_BLOW_FOOT] too, face and feet. */
    REAR_BLOW_BODY(7, 46),
    REAR_BLOW_FOOT(7, 47),
    /** The rear-seat climate controls are locked out. */
    REAR_LOCK(7, 67),

    /**
     * The canbus id: which vehicle the unit is configured for.
     *
     * It selects the vehicle's command table: the service records it as the
     * [SavedVehicle] id and [resolveVehicle] looks it up in [VehicleCatalog].
     * Measured on the WK2, it arrives about 40 ms after registration,
     * unprompted. See `docs/captures/2026-09-09-air-profile-extraction.md`.
     *
     * `com.syu.air` reads exactly this code and passes the value straight to
     * `AirFactory.create`, which switches it onto one of 222 vehicle profile
     * classes. It never queries the code either; it waits for the callback,
     * which is consistent with registration being the only read path.
     *
     * It stays `diagnostic`: the id is cached between boots, so a late or
     * absent report must not hold the re-registration retry open. A vehicle
     * that never reports it and has no cached id is simply never drawn for.
     * [CanbusId] decodes it.
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

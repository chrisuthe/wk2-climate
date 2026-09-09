package com.wk2.climate.bus

/**
 * A **write** on the vendor bus.
 *
 * This type deliberately hides two unrelated calling conventions:
 *  - climate is `module 7, code 6, {1, index}` where index is per-vehicle
 *  - volume is `module 4, code 0, {sentinel}`
 *
 * Keeping it separate from [Signal] is not decoration. Module 4 code 0 is
 * `C_VOL` for writes and `U_SPECTRUM` for reads — with a single untyped
 * `send(module, code, payload)` that collision eventually becomes a bug.
 *
 * Indices are **specific to the Jeep Grand Cherokee WK2**, whose vehicle
 * profile is `Car_0374_PA_Jeep_Wrangler` -- reached from canbus id 2621814
 * (`CAR_PA_Cherokee_14_22`), despite the class name. That profile declares 18
 * of the 19 commands below with exactly these indices; the one it does not is
 * MAX_AC, fitted here but absent from the factory bar.
 *
 * An earlier revision attributed this to `Car_0374_PA_Jeep_All`, which is the
 * Renegade and declares 6. Another vehicle needs its own table.
 */
enum class Command(
    val module: Int,
    val code: Int,
    val payload: IntArray,
    /** True only for actions that can leave the vehicle without climate control. */
    val destructive: Boolean = false,
) {
    // ---- module 7: CANBUS. code 6 is fixed; payload[1] is the vehicle index ----
    AC(7, 6, intArrayOf(1, 1)),

    /** Macro: also forces A/C on, clears body-blow, and sets fan to the AUTO sentinel. */
    AUTO(7, 6, intArrayOf(1, 2)),

    RECIRC(7, 6, intArrayOf(1, 3)),
    TEMP_L_UP(7, 6, intArrayOf(1, 4)),
    TEMP_L_DOWN(7, 6, intArrayOf(1, 5)),

    /** Exits AUTO as a side effect, landing on fan level 3. Idempotent at 7. */
    FAN_UP(7, 6, intArrayOf(1, 6)),
    FAN_DOWN(7, 6, intArrayOf(1, 7)),

    // Idempotent mode setters. This is what makes direct selection possible
    // instead of the OEM bar's four-step cycle.
    AIRFLOW_FACE(7, 6, intArrayOf(1, 8)),
    AIRFLOW_FACE_FEET(7, 6, intArrayOf(1, 9)),
    AIRFLOW_FEET(7, 6, intArrayOf(1, 10)),
    AIRFLOW_FEET_GLASS(7, 6, intArrayOf(1, 11)),

    /** Macro: forces recirculation on and fan to 6. Toggles against [MAX_AC]. */
    FRONT_DEFROST(7, 6, intArrayOf(1, 12)),

    /** The control the OEM bar labels DUAL. It drives `U_AIR_SYNC`, not `U_AIR_DUAL`. */
    SYNC(7, 6, intArrayOf(1, 13)),

    REAR_DEFROST(7, 6, intArrayOf(1, 14)),

    /** Macro: drives both temperatures to the LO sentinel. Discards the user's setpoints. */
    MAX_AC(7, 6, intArrayOf(1, 15)),

    /**
     * Powers the climate system off. This vehicle has no physical HVAC
     * controls, so this leaves no way to change anything until sent again.
     * Reachable only behind a hold-to-confirm gesture.
     */
    CLIMATE_POWER(7, 6, intArrayOf(1, 16), destructive = true),

    SEAT_HEAT_L(7, 6, intArrayOf(1, 17)),
    SEAT_HEAT_R(7, 6, intArrayOf(1, 18)),
    TEMP_R_UP(7, 6, intArrayOf(1, 20)),
    TEMP_R_DOWN(7, 6, intArrayOf(1, 21)),
    SEAT_VENT_L(7, 6, intArrayOf(1, 22)),
    SEAT_VENT_R(7, 6, intArrayOf(1, 23)),
    WHEEL_HEAT(7, 6, intArrayOf(1, 24)),

    // ---- module 4: SOUND. code 0 is C_VOL; the payload is a single sentinel ----
    VOL_UP(4, 0, intArrayOf(-1)),
    VOL_DOWN(4, 0, intArrayOf(-2)),

    /**
     * Hides the OEM volume OSD. Measured 2026-09-08: a bus-driven volume change
     * did not raise the OSD at all, so this is probably unnecessary. Kept
     * defined; do not build anything that depends on needing it.
     */
    VOL_HIDE_OSD(4, 0, intArrayOf(-7)),
    ;

    /** The per-vehicle index, for module 7 commands only. */
    val vehicleIndex: Int? get() = if (module == 7) payload.getOrNull(1) else null
}

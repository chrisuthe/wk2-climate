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
 * A climate entry names a control, not an index. The index is per vehicle and
 * comes from that vehicle's [CommandTable], which `vehicles.tsv` supplies;
 * [frame] turns the pair into the payload `{1, index}` at send time. A control
 * the vehicle's table lacks has no frame and is never sent.
 *
 * The comments below describe what each control does on the WK2 Grand
 * Cherokee (canbus id 2687350, profile `Car_0374_PA_Jeep_Wrangler`), where
 * every behaviour was measured.
 */
enum class Command(
    val module: Int,
    val code: Int,
    /** Volume only: the fixed sentinel. Climate payloads come from [frame]. */
    val payload: IntArray = IntArray(0),
    /** True only for actions that can leave the vehicle without climate control. */
    val destructive: Boolean = false,
) {
    // ---- module 7: CANBUS. code 6 is fixed; the index is the vehicle's ----
    AC(7, 6),

    /** Macro: also forces A/C on, clears body-blow, and sets fan to the AUTO sentinel. */
    AUTO(7, 6),

    RECIRC(7, 6),
    TEMP_L_UP(7, 6),
    TEMP_L_DOWN(7, 6),

    /** Exits AUTO as a side effect, landing on fan level 3. Idempotent at 7. */
    FAN_UP(7, 6),
    FAN_DOWN(7, 6),

    // Idempotent mode setters. This is what makes direct selection possible
    // instead of the OEM bar's four-step cycle.
    AIRFLOW_FACE(7, 6),
    AIRFLOW_FACE_FEET(7, 6),
    AIRFLOW_FEET(7, 6),
    AIRFLOW_FEET_GLASS(7, 6),

    /** Macro: forces recirculation on and fan to 6. Toggles against [MAX_AC]. */
    FRONT_DEFROST(7, 6),

    /** The control the OEM bar labels DUAL. It drives `U_AIR_SYNC`, not `U_AIR_DUAL`. */
    SYNC(7, 6),

    REAR_DEFROST(7, 6),

    /** Macro: drives both temperatures to the LO sentinel. Discards the user's setpoints. */
    MAX_AC(7, 6),

    /**
     * Powers the climate system off. This vehicle has no physical HVAC
     * controls, so this leaves no way to change anything until sent again.
     * Reachable only behind a hold-to-confirm gesture.
     */
    CLIMATE_POWER(7, 6, destructive = true),

    SEAT_HEAT_L(7, 6),
    SEAT_HEAT_R(7, 6),
    TEMP_R_UP(7, 6),
    TEMP_R_DOWN(7, 6),
    SEAT_VENT_L(7, 6),
    SEAT_VENT_R(7, 6),
    WHEEL_HEAT(7, 6),

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
}

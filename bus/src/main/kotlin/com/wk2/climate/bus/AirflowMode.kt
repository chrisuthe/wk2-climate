package com.wk2.climate.bus

/**
 * Airflow destination.
 *
 * The bus does not expose this as an enum. It exposes three independent
 * booleans — `BLOW_UP`, `BLOW_BODY`, `BLOW_FOOT` — of which only four
 * combinations are named, plus all-clear.
 *
 * The write side *is* four discrete idempotent setters, which is what lets the
 * UI offer direct selection instead of the OEM bar's four-step cycle.
 */
enum class AirflowMode {
    FACE,
    FACE_FEET,
    FEET,
    FEET_GLASS,

    /**
     * No airflow flag set. **This is the vehicle's resting state while AUTO
     * owns airflow**, measured on vehicle with `U_AIR_AUTO = 1`. Expected and
     * correct: the driver has selected nothing, so no tile lights.
     */
    NONE,

    /**
     * A flag combination we do not recognise. Reachable after the front
     * defrost macro. Renders exactly like [NONE] — lighting a *wrong* tile is
     * worse than lighting none — but unlike NONE it is worth logging.
     */
    UNKNOWN,
    ;

    /** True only when the driver has actively chosen this mode. Drives tile fill. */
    val isDriverSelected: Boolean get() = this in selectable

    /** The idempotent setter for this mode, or null for the two readings. */
    val command: Command?
        get() = when (this) {
            FACE -> Command.AIRFLOW_FACE
            FACE_FEET -> Command.AIRFLOW_FACE_FEET
            FEET -> Command.AIRFLOW_FEET
            FEET_GLASS -> Command.AIRFLOW_FEET_GLASS
            NONE, UNKNOWN -> null
        }

    companion object {
        /** The four tiles, in the order screen 1d draws them. */
        val selectable: List<AirflowMode> = listOf(FACE, FACE_FEET, FEET, FEET_GLASS)

        fun from(up: Int?, body: Int?, foot: Int?): AirflowMode {
            if (up == null || body == null || foot == null) return UNKNOWN
            return when {
                up == 0 && body == 1 && foot == 0 -> FACE
                up == 0 && body == 1 && foot == 1 -> FACE_FEET
                up == 0 && body == 0 && foot == 1 -> FEET
                up == 1 && body == 0 && foot == 1 -> FEET_GLASS
                up == 0 && body == 0 && foot == 0 -> NONE
                else -> UNKNOWN
            }
        }
    }
}

package com.wk2.climate.bus

/**
 * A zone setpoint temperature.
 *
 * The bus uses out-of-range sentinels for conditions that are not
 * temperatures. Those must render as a dimmed, non-committal state — never as
 * a number, and never as OFF.
 */
sealed interface Temp {
    data class Degrees(val fahrenheit: Int) : Temp

    /** The minimum / LO sentinel. Set by the MAX A/C macro among others. */
    data object Lo : Temp

    /**
     * No value, or a sentinel we do not have a meaning for. Also what the
     * vehicle reports for hardware that is not fitted — measured on vehicle,
     * `U_AIR_REAR_TEMP_LEFT` read `-2` with no rear zone installed.
     */
    data object Unavailable : Temp

    companion object {
        const val SENTINEL_LO = -2

        fun from(raw: Int?): Temp = when {
            raw == null -> Unavailable
            raw == SENTINEL_LO -> Lo
            raw < 0 -> Unavailable
            else -> Degrees(raw)
        }
    }
}

/**
 * Blower level.
 *
 * The manual range is 0..7. **15 is not a fan speed** — it is the sentinel the
 * vehicle reports while AUTO owns the blower. Measured on vehicle: with AUTO
 * engaged the bus reported 15 while the first FAN_UP landed on 3, so 15 does
 * not even indicate a high speed.
 */
sealed interface Fan {
    data class Level(val step: Int) : Fan

    /** AUTO owns the blower. Render as AUTO, never as a number. */
    data object Auto : Fan

    data object Unavailable : Fan

    companion object {
        /** Measured on vehicle: FAN_UP clamps here and stops reporting updates. */
        const val MAX_STEP = 7
        const val SENTINEL_AUTO = 15

        fun from(raw: Int?): Fan = when {
            raw == null -> Unavailable
            raw == SENTINEL_AUTO -> Auto
            raw in 0..MAX_STEP -> Level(raw)
            else -> Unavailable
        }
    }
}

/**
 * Seat heat / ventilation level, as presented.
 *
 * The protocol range is 0..3, but the cycle command visits only 0, 3 and 1 —
 * measured on vehicle as `0 -> 3 -> 1 -> 0`. State 2 is unreachable through
 * the cycle, so three presented states lose nothing.
 */
enum class SeatLevel {
    OFF, LOW, HIGH, UNAVAILABLE;

    val isOn: Boolean get() = this == LOW || this == HIGH

    /** Number of lit pips in the two-pip indicator. */
    val litPips: Int get() = when (this) {
        OFF, UNAVAILABLE -> 0
        LOW -> 1
        HIGH -> 2
    }

    companion object {
        fun from(raw: Int?): SeatLevel = when (raw) {
            0 -> OFF
            1 -> LOW
            2, 3 -> HIGH
            else -> UNAVAILABLE
        }
    }
}

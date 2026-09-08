package com.wk2.climate.bus

/**
 * An immutable snapshot of everything the vehicle has told us.
 *
 * Raw values are kept in a map keyed by [Signal] and every meaningful value is
 * *derived*. That is deliberate:
 *
 *  - the UI renders entirely from this, so there is one place sentinels are
 *    interpreted and no opportunity to interpret them differently twice;
 *  - adding a signal does not mean editing a wide constructor;
 *  - protocol behaviour we do not model — seat heat and vent are mutually
 *    exclusive, the macros force recirculation on — appears on its own,
 *    because we never write local state and the vehicle reports the truth.
 */
class ClimateState private constructor(private val raw: Map<Signal, Int>) {

    /** Returns `this` unchanged if the value is already stored, so StateFlow does not re-emit. */
    fun with(signal: Signal, value: Int): ClimateState =
        if (raw[signal] == value) this else ClimateState(raw + (signal to value))

    operator fun get(signal: Signal): Int? = raw[signal]

    /** True if the vehicle has ever reported this signal. Distinguishes absent from zero. */
    fun has(signal: Signal): Boolean = raw.containsKey(signal)

    val isEmpty: Boolean get() = raw.isEmpty()

    // ---- derived: zone temperatures ----
    val tempLeft: Temp get() = Temp.from(raw[Signal.TEMP_LEFT])
    val tempRight: Temp get() = Temp.from(raw[Signal.TEMP_RIGHT])

    // ---- derived: blower ----
    val fan: Fan get() = Fan.from(raw[Signal.WIND_LEVEL])

    // ---- derived: airflow ----
    val airflow: AirflowMode
        get() = AirflowMode.from(
            up = raw[Signal.BLOW_UP],
            body = raw[Signal.BLOW_BODY],
            foot = raw[Signal.BLOW_FOOT],
        )

    // ---- derived: comfort ----
    val seatHeatL: SeatLevel get() = SeatLevel.from(raw[Signal.SEAT_HEAT_L])
    val seatHeatR: SeatLevel get() = SeatLevel.from(raw[Signal.SEAT_HEAT_R])
    val seatVentL: SeatLevel get() = SeatLevel.from(raw[Signal.SEAT_VENT_L])
    val seatVentR: SeatLevel get() = SeatLevel.from(raw[Signal.SEAT_VENT_R])
    val wheelHeatOn: Boolean get() = flag(Signal.WHEEL_HEAT)

    // ---- derived: mode toggles ----
    val acOn: Boolean get() = flag(Signal.AC)
    val autoOn: Boolean get() = flag(Signal.AUTO)
    val recircOn: Boolean get() = flag(Signal.RECIRC)
    val maxAcOn: Boolean get() = flag(Signal.AC_MAX)
    val frontDefrostOn: Boolean get() = flag(Signal.FRONT_DEFROST)
    val rearDefrostOn: Boolean get() = flag(Signal.REAR_DEFROST)

    /** The control the OEM labels DUAL. It drives SYNC, not `U_AIR_DUAL`. */
    val syncOn: Boolean get() = flag(Signal.SYNC)

    val powerOn: Boolean get() = flag(Signal.POWER)

    // ---- derived: other modules ----
    val volume: Int? get() = raw[Signal.VOLUME]

    /** Day/night follows the vehicle's illumination signal, never a clock. */
    val isNight: Boolean get() = flag(Signal.ILLUMINATION)

    private fun flag(signal: Signal): Boolean = raw[signal] == 1

    override fun equals(other: Any?): Boolean = other is ClimateState && other.raw == raw
    override fun hashCode(): Int = raw.hashCode()
    override fun toString(): String =
        "ClimateState(" + raw.entries.joinToString { "${it.key}=${it.value}" } + ")"

    companion object {
        val EMPTY = ClimateState(emptyMap())

        fun of(vararg pairs: Pair<Signal, Int>): ClimateState =
            pairs.fold(EMPTY) { acc, (s, v) -> acc.with(s, v) }
    }
}

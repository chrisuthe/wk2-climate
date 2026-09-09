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

    /**
     * True once the vehicle has reported **any** climate signal.
     *
     * The single gate that lets the UI say "I don't know" instead of "off".
     * [flag] is `raw[signal] == 1`, so an absent signal and a signal reporting
     * zero both read as `false`; on an ignition cycle the head unit restarts,
     * this process comes up with an empty map, registration succeeds for all
     * 20 climate codes, and the vendor service — which notifies on *change*
     * only — says nothing until the driver moves something. Every boolean
     * control then paints a confident OFF for state we do not have, which is
     * what made the bar read as "the HVAC is off" rather than "no data yet".
     *
     * Only [Signal.MODULE_CANBUS] counts. Volume arrives on SOUND and
     * illumination on MAIN, and both can be present and updating while climate
     * is still silent — gating on "something arrived" would restore the lie.
     *
     * Making every flag nullable instead would ripple through the whole UI for
     * no benefit: absence is not per-signal here, it is the whole module being
     * quiet, so one gate carries the same information.
     */
    val hasClimateData: Boolean
        get() = raw.keys.any { it.module == Signal.MODULE_CANBUS }

    /**
     * Every [Signal] we registered for and have still never been told a value
     * for, in declaration order.
     *
     * The stopping rule for the re-registration retry. `hasClimateData` is the
     * wrong rule for it: the MCU streams climate frames continuously, so on
     * the vehicle climate landed within a second of registering — measured,
     * `climate data present after 0 re-registration(s)` — and the retry
     * finished on its first check without ever giving the signals that *do*
     * need nagging a chance. Those are the ones that only change when someone
     * acts: `VOLUME` until the knob turns, `ILLUMINATION` until the headlights
     * switch, `TEMP_OUT` until the reading moves.
     *
     * Registered-for and expected are the same thing here — [Signal.entries]
     * is exactly the list handed to `register` — so there is no second list to
     * keep in step.
     *
     * Empty is the goal state, not the normal one: some of these may simply
     * not exist on this vehicle, so a non-empty set after the last attempt is
     * information, not a fault. It is logged for that reason.
     */
    val missingSignals: Set<Signal>
        get() = Signal.entries.filterNotTo(LinkedHashSet()) { raw.containsKey(it) }

    // ---- derived: zone temperatures ----
    val tempUnit: TempUnit get() = TempUnit.from(raw[Signal.TEMP_UNIT])
    val tempLeft: Temp get() = Temp.from(raw[Signal.TEMP_LEFT], tempUnit)
    val tempRight: Temp get() = Temp.from(raw[Signal.TEMP_RIGHT], tempUnit)

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

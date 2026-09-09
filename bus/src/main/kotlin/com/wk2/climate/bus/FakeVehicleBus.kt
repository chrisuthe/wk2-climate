package com.wk2.climate.bus

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * An in-memory [VehicleBus] for development and tests.
 *
 * This is **deliberately adversarial**. A fake that simply echoes what you set
 * proves nothing. Every rule below is documented or measured vehicle
 * behaviour, so a UI that works against this fake is a UI that will not be
 * surprised by the car:
 *
 *  - seat heat cycles `0 -> 3 -> 1 -> 0`, skipping 2 (measured)
 *  - AUTO toggles; engaging it forces A/C on, clears body-blow and sets fan
 *    to 15, and leaving it lands on fan 3 with FACE (measured)
 *  - `FAN_UP` exits AUTO as a side effect, landing on level 3 (measured)
 *  - fan clamps at 7 and reports nothing further (measured)
 *  - `FRONT_DEFROST` and `MAX_AC` force recirculation on
 *  - `MAX_AC` drives both temperatures to the LO sentinel, clears AUTO and
 *    takes the fan to 7 (measured)
 *  - `FRONT_DEFROST` leaves airflow in an unrecognised combination
 *  - seat heat and seat vent are mutually exclusive
 *  - airflow setters are idempotent
 */
class FakeVehicleBus(initial: ClimateState = VEHICLE_BASELINE) : VehicleBus {

    private val _state = MutableStateFlow(initial)
    override val state: StateFlow<ClimateState> = _state.asStateFlow()

    private val _connected = MutableStateFlow(true)
    override val connected: StateFlow<Boolean> = _connected.asStateFlow()

    private val _sent = mutableListOf<Command>()

    /** Everything dispatched so far, so a UI test can assert on dispatch. */
    val sent: List<Command> get() = _sent.toList()

    /** Push a value as though the vehicle reported it. For exercising awkward states. */
    fun inject(signal: Signal, value: Int) {
        _state.value = _state.value.with(signal, value)
    }

    /**
     * Replace the whole snapshot, as an ignition cycle does.
     *
     * `ClimateState.EMPTY` is the only way to reach the cold-start case from a
     * running harness: bound and connected, with the vehicle having reported
     * nothing. [inject] cannot get there — it only ever adds.
     */
    fun replace(state: ClimateState) {
        _state.value = state
    }

    fun setConnected(value: Boolean) {
        _connected.value = value
    }

    private var _refreshes = 0

    /** How many times [refresh] has been asked for. Lets a test assert on the schedule. */
    val refreshes: Int get() = _refreshes

    /**
     * Counts the request and reports nothing new.
     *
     * Deliberately **not** a replay: this fake's state is already whatever it
     * was constructed with, so re-emitting it would prove that a refresh
     * populates a bar that was never empty. The interesting case is
     * `FakeVehicleBus(ClimateState.EMPTY)`, where refreshing changes nothing —
     * which is exactly what re-registration does when the MCU is reporting no
     * climate frames, and the case the retry has to give up on. Use [inject]
     * to model a vehicle that does answer.
     */
    override fun refresh() {
        _refreshes++
    }

    override fun send(command: Command) {
        if (!_connected.value) return
        _sent += command
        _state.value = reduce(_state.value, command)
    }

    private fun reduce(s: ClimateState, command: Command): ClimateState = when (command) {
        Command.AC -> s.toggle(Signal.AC)
        Command.RECIRC -> s.toggle(Signal.RECIRC)
        // Toggles the DUAL flag. `syncOn` inverts it, so the fake's baseline
        // `SYNC to 1` means zones *independent* -- see Signal.SYNC.
        Command.SYNC -> s.toggle(Signal.SYNC)
        Command.REAR_DEFROST -> s.toggle(Signal.REAR_DEFROST)
        Command.WHEEL_HEAT -> s.toggle(Signal.WHEEL_HEAT)

        // AUTO **toggles** -- measured on the vehicle 2026-09-09, correcting an
        // earlier reading of the command table. Tapping it while engaged turns
        // it off, and the off state is fan **3** with **FACE**: byte for byte
        // the `exitAuto()` transition already measured via FAN_UP. Leaving AUTO
        // lands in the same place however you leave it.
        //
        // On: A/C forced on, body-blow cleared, fan to the AUTO sentinel.
        Command.AUTO ->
            if (s.autoOn) {
                s.exitAuto()
            } else {
                s.with(Signal.AUTO, 1)
                    .with(Signal.AC, 1)
                    .with(Signal.WIND_LEVEL, Fan.SENTINEL_AUTO)
                    .airflow(up = 0, body = 0, foot = 0)
            }

        Command.FAN_UP -> fanUp(s)
        Command.FAN_DOWN -> fanDown(s)

        Command.AIRFLOW_FACE -> s.airflow(up = 0, body = 1, foot = 0)
        Command.AIRFLOW_FACE_FEET -> s.airflow(up = 0, body = 1, foot = 1)
        Command.AIRFLOW_FEET -> s.airflow(up = 0, body = 0, foot = 1)
        Command.AIRFLOW_FEET_GLASS -> s.airflow(up = 1, body = 0, foot = 1)

        // Macro: recirc forced on, fan to 6, airflow left unrecognised.
        Command.FRONT_DEFROST -> s
            .toggle(Signal.FRONT_DEFROST)
            .with(Signal.RECIRC, 1)
            .with(Signal.AUTO, 0)
            .with(Signal.WIND_LEVEL, 6)
            .airflow(up = 1, body = 1, foot = 0)

        // Macro, measured on the vehicle 2026-09-09 with MAX A/C engaged:
        // both setpoints to the LO sentinel, recirculation forced on, A/C on,
        // **AUTO cleared**, and the fan driven to **7** -- the panel read
        // `7 / 7` with no AUTO label, so `WIND_LEVEL` genuinely moves rather
        // than the blower ramping invisibly.
        //
        // Clearing AUTO forces a concrete airflow for the same reason FAN_UP
        // does, and FACE is what was observed. See `exitAuto()`, which is the
        // same transition at fan 3.
        Command.MAX_AC -> s
            .toggle(Signal.AC_MAX)
            .with(Signal.AC, 1)
            .with(Signal.RECIRC, 1)
            .with(Signal.AUTO, 0)
            .with(Signal.WIND_LEVEL, Fan.MAX_STEP)
            .airflow(up = 0, body = 1, foot = 0)
            .with(Signal.TEMP_LEFT, Temp.SENTINEL_LO)
            .with(Signal.TEMP_RIGHT, Temp.SENTINEL_LO)

        Command.CLIMATE_POWER -> s.toggle(Signal.POWER)

        Command.TEMP_L_UP -> s.stepTemp(Signal.TEMP_LEFT, +1)
        Command.TEMP_L_DOWN -> s.stepTemp(Signal.TEMP_LEFT, -1)
        Command.TEMP_R_UP -> s.stepTemp(Signal.TEMP_RIGHT, +1)
        Command.TEMP_R_DOWN -> s.stepTemp(Signal.TEMP_RIGHT, -1)

        Command.SEAT_HEAT_L -> s.cycleSeat(Signal.SEAT_HEAT_L, Signal.SEAT_VENT_L)
        Command.SEAT_HEAT_R -> s.cycleSeat(Signal.SEAT_HEAT_R, Signal.SEAT_VENT_R)
        Command.SEAT_VENT_L -> s.cycleSeat(Signal.SEAT_VENT_L, Signal.SEAT_HEAT_L)
        Command.SEAT_VENT_R -> s.cycleSeat(Signal.SEAT_VENT_R, Signal.SEAT_HEAT_R)

        Command.VOL_UP -> s.stepVolume(+1)
        Command.VOL_DOWN -> s.stepVolume(-1)
        Command.VOL_HIDE_OSD -> s   // affects the OEM overlay only, no state
    }

    // ---- behaviour helpers ----

    private fun fanUp(s: ClimateState): ClimateState {
        // Measured: the first press exits AUTO and lands on 3, materialising FACE.
        if (s.autoOn) return s.exitAuto()
        val current = s[Signal.WIND_LEVEL] ?: 0
        // Measured: clamps at 7 and reports no further update.
        if (current >= Fan.MAX_STEP) return s
        return s.with(Signal.WIND_LEVEL, current + 1)
    }

    private fun fanDown(s: ClimateState): ClimateState {
        if (s.autoOn) return s.exitAuto()
        val current = s[Signal.WIND_LEVEL] ?: 0
        if (current <= 0) return s
        return s.with(Signal.WIND_LEVEL, current - 1)
    }

    private fun ClimateState.exitAuto(): ClimateState =
        with(Signal.AUTO, 0)
            .with(Signal.WIND_LEVEL, 3)
            .airflow(up = 0, body = 1, foot = 0)

    private fun ClimateState.toggle(signal: Signal): ClimateState =
        with(signal, if (this[signal] == 1) 0 else 1)

    private fun ClimateState.airflow(up: Int, body: Int, foot: Int): ClimateState =
        with(Signal.BLOW_UP, up).with(Signal.BLOW_BODY, body).with(Signal.BLOW_FOOT, foot)

    private fun ClimateState.stepTemp(signal: Signal, delta: Int): ClimateState {
        val current = this[signal] ?: return this
        // Measured on vehicle: sweeping the setpoint steps *into* the LO/HI
        // sentinels at the boundaries rather than clamping, so a UI built
        // against this fake will see them too.
        val next = when {
            current == Temp.SENTINEL_LO && delta > 0 -> TEMP_MIN
            current == Temp.SENTINEL_LO -> current
            current == Temp.SENTINEL_HI && delta < 0 -> TEMP_MAX
            current == Temp.SENTINEL_HI -> current
            current == TEMP_MAX && delta > 0 -> Temp.SENTINEL_HI
            current == TEMP_MIN && delta < 0 -> Temp.SENTINEL_LO
            else -> (current + delta).coerceIn(TEMP_MIN, TEMP_MAX)
        }
        return with(signal, next)
    }

    private fun ClimateState.cycleSeat(level: Signal, opposite: Signal): ClimateState {
        // Measured cycle: 0 -> 3 -> 1 -> 0. State 2 is never visited.
        val next = when (this[level] ?: 0) {
            0 -> 3
            3 -> 1
            else -> 0
        }
        val stepped = with(level, next)
        // Mutual exclusion is enforced in the protocol, not just the UI.
        return if (next != 0) stepped.with(opposite, 0) else stepped
    }

    private fun ClimateState.stepVolume(delta: Int): ClimateState {
        val current = this[Signal.VOLUME] ?: 0
        return with(Signal.VOLUME, (current + delta).coerceIn(0, VOLUME_MAX))
    }

    companion object {
        const val TEMP_MIN = 60
        const val TEMP_MAX = 84
        const val VOLUME_MAX = 40

        /**
         * The exact snapshot captured from the vehicle on 2026-09-08: engine
         * accessory on, AUTO engaged, both zones at 68 F, fan reporting the
         * AUTO sentinel, and all three airflow flags clear.
         */
        val VEHICLE_BASELINE: ClimateState = ClimateState.of(
            Signal.POWER to 1,
            Signal.AC to 1,
            Signal.AUTO to 1,
            Signal.SYNC to 1,
            Signal.RECIRC to 0,
            Signal.AC_MAX to 0,
            Signal.FRONT_DEFROST to 0,
            Signal.REAR_DEFROST to 0,
            Signal.WIND_LEVEL to Fan.SENTINEL_AUTO,
            Signal.TEMP_LEFT to 68,
            Signal.TEMP_RIGHT to 68,
            Signal.TEMP_UNIT to 1,
            Signal.BLOW_UP to 0,
            Signal.BLOW_BODY to 0,
            Signal.BLOW_FOOT to 0,
            Signal.SEAT_HEAT_L to 0,
            Signal.SEAT_HEAT_R to 0,
            Signal.SEAT_VENT_L to 0,
            Signal.SEAT_VENT_R to 0,
            Signal.WHEEL_HEAT to 0,
            Signal.VOLUME to 10,
            Signal.ILLUMINATION to 0,
        )
    }
}

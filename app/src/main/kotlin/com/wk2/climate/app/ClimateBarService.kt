package com.wk2.climate.app

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.wk2.climate.bus.AdaptiveSlot
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.ConnectionGate.followConnection
import com.wk2.climate.bus.Signal
import com.wk2.climate.bus.SyuVehicleBus
import com.wk2.climate.bus.TempUnit
import com.wk2.climate.design.Dimens
import com.wk2.climate.ui.bar.ClimateBar
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Owns the replacement bar.
 *
 * An `AccessibilityService` rather than a foreground service plus a 2038
 * overlay, because HOME and BACK have **no other route** for a third-party app
 * — so one of these is required regardless, and this adds nothing else. It also
 * needs no `SYSTEM_ALERT_WINDOW`, and a 2032 window is a *trusted* overlay that
 * the system does not hide during permission dialogs.
 *
 * `com.syu.air` keeps running underneath. This is an alternative front end, not
 * a replacement service: if this dies, the factory bar is still there, and a
 * vehicle with no physical HVAC controls is never left unable to change its
 * climate.
 */
class ClimateBarService : AccessibilityService() {

    private lateinit var bus: SyuVehicleBus
    private lateinit var barHost: ComposeOverlayHost
    private val slot = AdaptiveSlot()

    /**
     * The service's own scope, created in [onServiceConnected] and cancelled in
     * [teardown]. Nothing here may outlive the connection: a collector still
     * running against a disconnected bus could re-add the bar window after the
     * service was told to stop, and a 2032 overlay nothing owns any more is the
     * one failure this file exists to prevent.
     */
    private var scope: CoroutineScope? = null

    /**
     * The framework may call this more than once — re-enabling or
     * reconfiguring the service on an always-on head unit is exactly when it
     * would — so tear down first and start clean.
     *
     * Tearing down rather than early-returning: an early return would keep
     * whatever host and bus the previous connection left behind, and if that
     * connection's window token is already gone the bar is dead with no way to
     * rebuild it. Overwriting the fields without tearing down is worse still —
     * the old `ComposeView` stays added to the `WindowManager` and the old bus
     * stays bound, both unreachable, which would strand a 2032 overlay over
     * the factory bar that nothing short of a reboot could clear. [teardown]
     * is safe on an uninitialised or already-torn-down state.
     */
    override fun onServiceConnected() {
        teardown()
        bus = SyuVehicleBus(this).also { it.connect() }
        barHost = ComposeOverlayHost(this)
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also {
            scope = it
            it.launch { followBus() }
        }
    }

    /**
     * Ties the bar window's existence to [VehicleBus.connected].
     *
     * Design rule 6: `com.syu.air` keeps running underneath, and nothing we do
     * may leave the vehicle with no climate control. Our window consumes
     * **every** touch in `[0,1693][1080,1920]` with zero leakage — measured on
     * the vehicle — so if the bus is dead the bar is not merely useless, it is
     * an opaque lid over the only working climate UI in a vehicle with no
     * physical HVAC controls. Removing the window hands the factory bar back,
     * unambiguously and immediately.
     *
     * `FLAG_NOT_TOUCHABLE` is deliberately *not* the fallback. A window that
     * still draws our layout while taps fall through to whatever factory
     * control happens to sit at that coordinate is worse than either extreme:
     * the driver aims at our AUTO button and hits something else. A UI that
     * lies about what a touch does is more dangerous than no UI.
     *
     * The bar is shown up front and only hidden if the bus is still down after
     * [BUS_GRACE_MS], so an ordinarily slow `bindService` does not flash the
     * factory bar into view. An already-connected bus skips the wait entirely.
     * After that the window simply follows the flow, so a bus that comes back
     * brings the bar back with it.
     *
     * The gating algorithm itself is [ConnectionGate.followConnection], kept
     * in `:bus` so it is provable with virtual time; this is just the Android
     * wiring — which flow, which grace, which window calls.
     */
    private suspend fun followBus() {
        followConnection(bus.connected, BUS_GRACE_MS, ::showBar, ::hideBar)
    }

    private fun showBar() {
        // `addView` on a raw overlay window can genuinely fail — a bad token, a
        // window type the platform rejects, a revoked permission. Left
        // unhandled the service would sit half-initialised with a live bus and
        // no window, so tear down and let the factory bar carry on.
        try {
            barHost.show(barWindowParams()) { BarContent() }
        } catch (t: Throwable) {
            Log.e(TAG, "could not add the bar window — the factory bar remains", t)
            teardown()
        }
    }

    /**
     * Removes the bar window, leaving the factory bar visible and usable.
     *
     * `isShowing` is checked so this stays quiet when it is already hidden —
     * `ComposeOverlayHost.hide()` is itself guarded against a double hide, but
     * the warning must not repeat on every re-emission of `connected = false`.
     */
    private fun hideBar() {
        if (!barHost.isShowing) return
        barHost.hide()
        Log.w(
            TAG,
            "vehicle bus unavailable — removing the bar window so the com.syu.air " +
                "factory bar is usable again. Our overlay consumes every touch over " +
                "the factory bar, and this vehicle has no physical HVAC controls, so " +
                "a bar we cannot drive must not stay on screen. It returns as soon as " +
                "the bus does.",
        )
    }

    @Composable
    private fun BarContent() {
        val state by bus.state.collectAsState()

        // The slot is re-evaluated on a timer rather than per state change: its
        // own hysteresis and dwell decide whether anything moves, and outside
        // temperature moves far more slowly than the poll interval.
        var slotContent by remember { mutableStateOf(slot.content) }
        LaunchedEffect(Unit) {
            while (true) {
                slotContent = slot.update(outsideF(state), System.currentTimeMillis())
                delay(SLOT_POLL_MS)
            }
        }

        ClimateBar(
            state = state,
            slot = slotContent,
            onCommand = { bus.send(it) },
            onHome = { performGlobalAction(GLOBAL_ACTION_HOME) },
            onBack = { performGlobalAction(GLOBAL_ACTION_BACK) },
            onOpenClimate = { /* screen 1d arrives in plan 3 */ },
            onSlotPressChange = { down ->
                if (down) {
                    slot.onFingerDown()
                } else {
                    slot.onFingerUp()
                    // Arm the tap lockout on release: the slot must not change
                    // for a moment after the driver's finger leaves it, or
                    // their next press lands on a control they did not aim at.
                    // Release is the moment the tap completes — and taking it
                    // from here rather than from `onCommand` avoids
                    // duplicating the UI's SlotContent-to-Command mapping as a
                    // second source of truth. A press dragged off the slot
                    // arms it too, which only ever delays a swap.
                    slot.onTap(System.currentTimeMillis())
                }
            },
        )
    }

    /**
     * Outside temperature in whole degrees F, or null when we do not have it.
     *
     * `U_TEMP_OUT` packs tenths of a degree offset by 1000 into the low 16
     * bits, with bit 28 as a validity flag. Verified against the head unit's
     * own status bar on the vehicle: raw `0x10000744` -> 1860 tenths -> 86 F.
     *
     * Only trusted when the vehicle is reporting Fahrenheit. [AdaptiveSlot]'s
     * thresholds are Fahrenheit, and whether `U_TEMP_OUT` is unit-scaled the
     * way `TEMP_LEFT`/`TEMP_RIGHT` are is **unverified** — the vehicle
     * observation above was taken with the unit set to Fahrenheit, so it does
     * not distinguish the two. If it is scaled, a 30 C day would decode as
     * "30" and swing the slot to FRONT DEFROST in the heat. So this fails safe
     * rather than guessing.
     *
     * An invalid, out-of-range or non-Fahrenheit reading returns null, and
     * [AdaptiveSlot] pins to SEAT HEAT in that case — so the slot is never
     * blank and the bar's geometry never changes.
     */
    private fun outsideF(state: ClimateState): Int? {
        if (state.tempUnit != TempUnit.FAHRENHEIT) return null
        val raw = state[Signal.TEMP_OUT] ?: return null
        if ((raw shr 28) and 1 != 1) return null
        val f = ((raw and 0xFFFF) - 1000) / 10
        return if (f in OUTSIDE_F_MIN..OUTSIDE_F_MAX) f else null
    }

    /**
     * The verified parameters for covering the factory bar.
     *
     * Measured on the vehicle: this lands exactly on `[0,1693][1080,1920]` as
     * the topmost window. `FLAG_LAYOUT_NO_LIMITS` is load-bearing — without it
     * gravity resolves against the inset-reduced frame and the window cannot
     * enter the nav-bar region at all. Positioning from the TOP with an
     * absolute y sidesteps the negative-offset problem entirely.
     *
     * The height is the *design's* bar height, not the framework's
     * `navigation_bar_height`. The two coincide on the target ROM, but the
     * dimen is a reserved-inset value and need not equal the bar the system
     * draws — an emulator was observed reporting 56px against a 169px nav-bar
     * window. Sizing the window from the framework would silently clip screen
     * 2a to its top row on any hardware that disagrees, so we draw the bar at
     * the size it is designed for and [warnIfNavInsetDisagrees] makes a
     * mismatch a log line instead.
     */
    private fun barWindowParams(): WindowManager.LayoutParams {
        val height = designBarHeightPx()
        warnIfNavInsetDisagrees(height)
        val top = resources.displayMetrics.heightPixels - height
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            height,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = top
        }
    }

    /**
     * The one source of truth for the bar's height: the design constant screen
     * 2a is laid out against. Design rule 5 — the bar never grows — makes this
     * a fixed size, so the window must never be smaller than it.
     */
    private fun designBarHeightPx(): Int =
        (Dimens.barHeight.value * resources.displayMetrics.density).roundToInt()

    /**
     * Reads the framework's `navigation_bar_height` purely to compare.
     *
     * If it does not match the design height then this is not the hardware the
     * bar was measured on, and the layout's assumptions — the 227px the whole
     * of 2a is built to, which the handoff notes cannot be exceeded without
     * root — no longer hold. That is worth a warning, and it is much better
     * than the alternative symptom of rendering a fragment of the UI.
     */
    private fun warnIfNavInsetDisagrees(designHeightPx: Int) {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        if (id <= 0) return
        val inset = resources.getDimensionPixelSize(id)
        if (inset != designHeightPx) {
            Log.w(
                TAG,
                "navigation_bar_height is ${inset}px but the bar is designed at " +
                    "${designHeightPx}px — drawing at the design size. This is not the " +
                    "hardware the bar was measured on; check what covers the factory bar.",
            )
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        teardown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        teardown()
        super.onDestroy()
    }

    private fun teardown() {
        // Cancelled before the window goes, so the collector cannot re-add it.
        scope?.cancel()
        scope = null
        if (::barHost.isInitialized) barHost.destroy()
        if (::bus.isInitialized) bus.disconnect()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private companion object {
        private const val TAG = "ClimateBarService"

        /**
         * How long a slow `bindService` is given before the bar is pulled.
         *
         * Long enough that a cold vendor-service start does not expose the
         * factory bar; short enough that a driver is not left tapping a dead
         * bar. The vehicle's own bind completes well inside this.
         */
        const val BUS_GRACE_MS = 4_000L

        /** The slot's own dwell is 30s, so polling faster than this buys nothing. */
        const val SLOT_POLL_MS = 5_000L

        /** Sanity window for a decoded outside temperature. */
        const val OUTSIDE_F_MIN = -60
        const val OUTSIDE_F_MAX = 160
    }
}

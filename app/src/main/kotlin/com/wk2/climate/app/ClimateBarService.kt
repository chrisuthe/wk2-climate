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
import com.wk2.climate.bus.Signal
import com.wk2.climate.bus.SyuVehicleBus
import com.wk2.climate.ui.bar.ClimateBar
import kotlinx.coroutines.delay

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

    override fun onServiceConnected() {
        bus = SyuVehicleBus(this).also { it.connect() }
        barHost = ComposeOverlayHost(this)
        showBar()
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
                if (down) slot.onFingerDown() else slot.onFingerUp()
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
     * An invalid or out-of-range reading returns null, and [AdaptiveSlot] pins
     * to SEAT HEAT in that case — so the slot is never blank and the bar's
     * geometry never changes.
     */
    private fun outsideF(state: ClimateState): Int? {
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
     */
    private fun barWindowParams(): WindowManager.LayoutParams {
        val height = navigationBarHeightPx()
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
     * The bar's height is the framework's own `navigation_bar_height`, which is
     * where the 227px comes from in the first place. Reading it keeps us aligned
     * with the inset the system reserves instead of asserting a number.
     */
    private fun navigationBarHeightPx(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else BAR_HEIGHT_FALLBACK_PX
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
        if (::barHost.isInitialized) barHost.destroy()
        if (::bus.isInitialized) bus.disconnect()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private companion object {
        private const val TAG = "ClimateBarService"

        /**
         * Fallback for the framework's `navigation_bar_height`, which this ROM
         * raises from AOSP's 48dp to 227px. Read the real value where possible
         * rather than trusting this.
         */
        const val BAR_HEIGHT_FALLBACK_PX = 227

        /** The slot's own dwell is 30s, so polling faster than this buys nothing. */
        const val SLOT_POLL_MS = 5_000L

        /** Sanity window for a decoded outside temperature. */
        const val OUTSIDE_F_MIN = -60
        const val OUTSIDE_F_MAX = 160
    }
}

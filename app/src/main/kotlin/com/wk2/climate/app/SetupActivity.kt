package com.wk2.climate.app

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type

/**
 * The only activity, and it exists for one reason: to get the driver to the
 * screen that enables the accessibility service.
 *
 * The bar **is** an accessibility service, and Android grants
 * `BIND_ACCESSIBILITY_SERVICE` only when the user enables it in Settings.
 * There is no permission to request — it is not a runtime permission, and
 * enabling it programmatically needs `WRITE_SECURE_SETTINGS`, which is
 * `signature|privileged`. So an app cannot grant this to itself, by design:
 * an accessibility service can read the screen and inject actions, so Android
 * treats self-enabling as the attack it resembles.
 *
 * What an app *can* do is stop the user hunting. On the FYT ROM this was built
 * against, Settings' own search bar is inert — `com.android.settings.intelligence`
 * is installed but does nothing — and the Accessibility row is not where a
 * stock Android 13 build puts it. The activity is reachable
 * (`com.android.settings.Settings$AccessibilitySettingsActivity`) and the
 * standard intent resolves; only the navigation to it is missing. So this
 * screen deep-links straight there.
 *
 * With no activity at all, none of our code ever runs until the service is
 * enabled — which is the chicken-and-egg this fixes. The cost is a launcher
 * icon, which the app previously did not have.
 */
class SetupActivity : ComponentActivity() {

    private var jumpedToSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { SetupScreen() }
    }

    override fun onResume() {
        super.onResume()
        // Re-checked on every resume, so coming back from Settings shows the
        // new state rather than a stale one.
        state = readState()

        // Jump straight to Settings the first time, and only when it is
        // actually needed: tapping the icon is then two taps from a working
        // bar. Never repeat it -- returning from Settings without enabling
        // would otherwise bounce the driver back out immediately.
        if (state == Setup.DISABLED && !jumpedToSettings) {
            jumpedToSettings = true
            openAccessibilitySettings()
        }
    }

    private fun readState(): Setup {
        val manager = getSystemService(AccessibilityManager::class.java)
            ?: return Setup.DISABLED
        val ours = ComponentName(this, ClimateBarService::class.java)
        val enabled = manager
            .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            .any { ComponentName.unflattenFromString(it.id) == ours }
        return if (enabled) Setup.ENABLED else Setup.DISABLED
    }

    private fun openAccessibilitySettings() {
        try {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } catch (t: ActivityNotFoundException) {
            // Some vendor ROMs strip the screen entirely. Nothing an app can do
            // about that, so say so rather than failing silently.
            Log.w(TAG, "no accessibility settings screen on this ROM", t)
            state = Setup.NO_SETTINGS_SCREEN
        }
    }

    @Composable
    private fun SetupScreen() {
        val palette = Palette.NIGHT
        Column(
            Modifier
                .fillMaxSize()
                .background(palette.surface)
                .padding(34.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            BasicText(
                text = getString(
                    when (state) {
                        Setup.ENABLED -> R.string.setup_enabled_title
                        Setup.DISABLED -> R.string.setup_disabled_title
                        Setup.NO_SETTINGS_SCREEN -> R.string.setup_nosettings_title
                    },
                ),
                style = Type.panelOutside.copy(color = palette.ink),
            )
            Spacer(Modifier.height(18.dp))
            BasicText(
                // Copy lives in strings.xml. It is the only user-facing prose
                // in the project, and keeping it there also keeps the arrows
                // and quotation marks out of Kotlin source, which stays ASCII.
                text = getString(
                    when (state) {
                        Setup.ENABLED -> R.string.setup_enabled_body
                        Setup.DISABLED -> R.string.setup_disabled_body
                        Setup.NO_SETTINGS_SCREEN -> R.string.setup_nosettings_body
                    },
                ),
                style = Type.panelStatus.copy(color = palette.inkMuted),
            )
        }
    }

    private companion object {
        private const val TAG = "SetupActivity"
    }
}

private enum class Setup { ENABLED, DISABLED, NO_SETTINGS_SCREEN }

/**
 * Held outside the activity so a configuration change does not lose it. It is
 * re-read in `onResume` regardless, so this only avoids a flicker.
 */
private var state by mutableStateOf(Setup.DISABLED)

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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.CanbusId
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Features
import com.wk2.climate.bus.Question
import com.wk2.climate.bus.SavedVehicle
import com.wk2.climate.bus.VehicleCatalog
import com.wk2.climate.bus.VehicleStatus
import com.wk2.climate.bus.questionsFor
import com.wk2.climate.bus.resolveVehicle
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.design.Type
import com.wk2.climate.ui.bar.ClimateBar
import com.wk2.climate.ui.rememberPressState
import com.wk2.climate.ui.target

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
 *
 * Once the service is enabled this is also where the car is set up, once:
 * the service records the canbus id in [VehicleStore], and this screen names
 * the car, asks the setup questions one per screen, and afterwards shows the
 * answers as switches. Nothing here talks to the vehicle bus.
 */
class SetupActivity : ComponentActivity() {

    private var jumpedToSettings = false

    private lateinit var store: VehicleStore
    private val catalog by lazy { VehicleCatalog.bundled() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = VehicleStore(this)
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
        val saved by store.saved.collectAsState()
        // Activity-local: reopening the app mid-setup shows the car again
        // before resuming at the first unanswered question.
        var confirmed by remember { mutableStateOf(false) }

        Column(
            Modifier
                .fillMaxSize()
                .background(palette.surface)
                .padding(34.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            when (state) {
                Setup.DISABLED -> {
                    Message(palette, getString(R.string.setup_disabled_title), getString(R.string.setup_disabled_body))
                    Spacer(Modifier.height(40.dp))
                    // The automatic jump happens once per activity; this is the way
                    // back after leaving Settings without enabling the service.
                    BigButton(palette, getString(R.string.setup_open_settings), filled = true) {
                        openAccessibilitySettings()
                    }
                }
                Setup.NO_SETTINGS_SCREEN ->
                    Message(palette, getString(R.string.setup_nosettings_title), getString(R.string.setup_nosettings_body))
                Setup.ENABLED -> VehicleSetup(palette, saved, confirmed, onConfirm = { confirmed = true })
            }
        }
    }

    /** Everything after the service is enabled, chosen by the vehicle status. */
    @Composable
    private fun ColumnScope.VehicleSetup(
        palette: Palette,
        saved: SavedVehicle,
        confirmed: Boolean,
        onConfirm: () -> Unit,
    ) {
        when (val status = resolveVehicle(saved, catalog)) {
            VehicleStatus.Waiting ->
                Message(palette, getString(R.string.setup_waiting_title), getString(R.string.setup_waiting_body))

            is VehicleStatus.Unknown -> {
                Message(palette, getString(R.string.setup_unknown_title), getString(R.string.setup_unknown_body))
                Spacer(Modifier.height(24.dp))
                Mono(
                    palette,
                    getString(R.string.setup_unknown_id, status.id, CanbusId.base(status.id), CanbusId.variant(status.id)),
                )
                Spacer(Modifier.height(40.dp))
                BigButton(palette, getString(R.string.setup_close), filled = false) { finish() }
            }

            is VehicleStatus.NeedsSetup ->
                if (!confirmed) {
                    Message(palette, getString(R.string.setup_found_title), getString(R.string.setup_found_body))
                    Spacer(Modifier.height(32.dp))
                    BasicText(status.vehicle.displayName, style = Type.panelTitle.copy(color = palette.ink))
                    Spacer(Modifier.height(10.dp))
                    Mono(palette, getString(R.string.setup_found_id, status.vehicle.id))
                    Spacer(Modifier.height(40.dp))
                    BigButton(palette, getString(R.string.setup_continue), filled = true, onClick = onConfirm)
                } else {
                    val q = status.questions.first { it !in status.answers }
                    QuestionScreen(
                        palette = palette,
                        number = status.questions.indexOf(q) + 1,
                        of = status.questions.size,
                        question = q,
                        // Unasked questions preview as YES, so the bar shows the car as it
                        // will be if the remaining answers are yes -- not a single-zone
                        // bar for a dual-zone car before that question comes up.
                        preview = Features(
                            status.vehicle.table,
                            Question.entries.associateWith { true } + status.answers + (q to true),
                        ),
                        onAnswer = { yes -> store.update { it.withAnswer(status.vehicle.id, q, yes) } },
                    )
                }

            is VehicleStatus.Ready -> {
                Mono(palette, getString(R.string.setup_your_car))
                Spacer(Modifier.height(10.dp))
                BasicText(status.vehicle.displayName, style = Type.panelTitle.copy(color = palette.ink))
                Spacer(Modifier.height(18.dp))
                BasicText(getString(R.string.setup_ready_body), style = Type.panelStatus.copy(color = palette.inkMuted))
                Spacer(Modifier.height(24.dp))
                for (q in questionsFor(status.vehicle.table)) {
                    val on = status.features.answers[q] == true
                    SwitchRow(palette, getString(q.label), on) {
                        store.update { it.withAnswer(status.vehicle.id, q, !on) }
                    }
                    Spacer(Modifier.height(Dimens.tileGap))
                }
                Spacer(Modifier.height(26.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.tileGap)) {
                    BigButton(palette, getString(R.string.setup_ask_again), filled = false, modifier = Modifier.weight(1f)) {
                        onConfirm()
                        store.update { it.askAgain(status.vehicle.id) }
                    }
                    // Every switch saves as it is flipped, so this only closes.
                    BigButton(palette, getString(R.string.setup_done), filled = true, modifier = Modifier.weight(1f)) {
                        finish()
                    }
                }
            }
        }
    }

    @Composable
    private fun QuestionScreen(
        palette: Palette,
        number: Int,
        of: Int,
        question: Question,
        preview: Features,
        onAnswer: (Boolean) -> Unit,
    ) {
        Mono(palette, getString(R.string.setup_question_count, number, of))
        Spacer(Modifier.height(18.dp))
        Message(palette, getString(question.title), getString(question.hint))
        Spacer(Modifier.height(36.dp))
        Mono(palette, getString(R.string.setup_preview_label))
        Spacer(Modifier.height(10.dp))
        // The real bar, scaled, drawn as if this answer were YES. It has no
        // vehicle state, so it shows its not-yet-live look: a picture of the
        // layout, not of the car.
        Box(Modifier.size(Dimens.barWidth * PREVIEW_SCALE, Dimens.barHeight * PREVIEW_SCALE)) {
            ClimateBar(
                state = ClimateState.EMPTY,
                features = preview,
                onCommand = {},
                onHome = {},
                onBack = {},
                panelOpen = false,
                onToggleClimate = {},
                seatMenuOpen = null,
                onSeatButton = {},
                // The bar is a fixed 1080 x 227: let it overflow the scaled box
                // from its top-left corner (requiredSize alone would centre the
                // overflow and push the left edge off-screen), then scale it
                // about that same corner.
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(Dimens.barWidth, Dimens.barHeight)
                    .graphicsLayer {
                        scaleX = PREVIEW_SCALE
                        scaleY = PREVIEW_SCALE
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            )
        }
        Spacer(Modifier.height(48.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Dimens.tileGap)) {
            BigButton(palette, getString(R.string.setup_no), filled = false, modifier = Modifier.weight(1f)) {
                onAnswer(false)
            }
            BigButton(palette, getString(R.string.setup_yes), filled = true, modifier = Modifier.weight(1f)) {
                onAnswer(true)
            }
        }
    }

    @Composable
    private fun Message(palette: Palette, title: String, body: String) {
        BasicText(text = title, style = Type.panelOutside.copy(color = palette.ink))
        if (body.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            // Copy lives in strings.xml. It is the only user-facing prose in
            // the project, and keeping it there also keeps the arrows and
            // quotation marks out of Kotlin source, which stays ASCII.
            BasicText(text = body, style = Type.panelStatus.copy(color = palette.inkMuted))
        }
    }

    @Composable
    private fun Mono(palette: Palette, text: String) =
        BasicText(text = text, style = Type.sectionHeader.copy(color = palette.inkMuted))

    /** A button well over the 96dp floor; filled for the forward action. */
    @Composable
    private fun BigButton(
        palette: Palette,
        label: String,
        filled: Boolean,
        modifier: Modifier = Modifier.fillMaxWidth(),
        onClick: () -> Unit,
    ) {
        val (interaction, pressed) = rememberPressState()
        val shape = RoundedCornerShape(Dimens.radiusTile)
        Box(
            modifier
                .height(BUTTON_HEIGHT)
                .background(
                    when {
                        filled -> palette.ink
                        pressed.value -> palette.surfaceRaised
                        else -> palette.surface
                    },
                    shape,
                )
                .border(Dimens.controlBorderWidth, palette.ink, shape)
                .target(interaction, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(label, style = Type.modeLabelLarge.copy(color = if (filled) palette.surface else palette.ink))
        }
    }

    /** One answer as a switch. The whole 96dp row is the target. */
    @Composable
    private fun SwitchRow(palette: Palette, label: String, on: Boolean, onToggle: () -> Unit) {
        val (interaction, pressed) = rememberPressState()
        val shape = RoundedCornerShape(Dimens.radiusTile)
        val pill = RoundedCornerShape(50)
        Row(
            Modifier
                .fillMaxWidth()
                .height(Dimens.minTarget)
                .background(if (pressed.value) palette.surfaceRaised else palette.surface, shape)
                .border(Dimens.controlBorderWidth, palette.borderControl, shape)
                .target(interaction, onClick = onToggle)
                .padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(label, style = Type.menuLabel.copy(color = palette.ink), modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .size(width = 76.dp, height = 40.dp)
                    .background(if (on) palette.accent else palette.surfaceInset, pill),
                contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .padding(4.dp)
                        .size(32.dp)
                        .background(if (on) palette.accentInk else palette.inkMuted, pill),
                )
            }
        }
    }

    private companion object {
        private const val TAG = "SetupActivity"
        private const val PREVIEW_SCALE = 0.8f
        private val BUTTON_HEIGHT = 120.dp
    }
}

private enum class Setup { ENABLED, DISABLED, NO_SETTINGS_SCREEN }

/**
 * Held outside the activity so a configuration change does not lose it. It is
 * re-read in `onResume` regardless, so this only avoids a flicker.
 */
private var state by mutableStateOf(Setup.DISABLED)

private val Question.title: Int
    get() = when (this) {
        Question.SEAT_HEAT -> R.string.setup_q_seat_heat_title
        Question.SEAT_VENT -> R.string.setup_q_seat_vent_title
        Question.WHEEL_HEAT -> R.string.setup_q_wheel_heat_title
        Question.DUAL_ZONE -> R.string.setup_q_dual_zone_title
        Question.REAR_CLIMATE -> R.string.setup_q_rear_climate_title
    }

private val Question.hint: Int
    get() = when (this) {
        Question.SEAT_HEAT -> R.string.setup_q_seat_heat_hint
        Question.SEAT_VENT -> R.string.setup_q_seat_vent_hint
        Question.WHEEL_HEAT -> R.string.setup_q_wheel_heat_hint
        Question.DUAL_ZONE -> R.string.setup_q_dual_zone_hint
        Question.REAR_CLIMATE -> R.string.setup_q_rear_climate_hint
    }

private val Question.label: Int
    get() = when (this) {
        Question.SEAT_HEAT -> R.string.setup_label_seat_heat
        Question.SEAT_VENT -> R.string.setup_label_seat_vent
        Question.WHEEL_HEAT -> R.string.setup_label_wheel_heat
        Question.DUAL_ZONE -> R.string.setup_label_dual_zone
        Question.REAR_CLIMATE -> R.string.setup_label_rear_climate
    }

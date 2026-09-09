package com.wk2.climate.harness

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wk2.climate.bus.AdaptiveSlot
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.FakeVehicleBus
import com.wk2.climate.bus.Signal
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.ui.bar.ClimateBar
import com.wk2.climate.ui.panel.ClimatePanel

/**
 * Development harness. **Not shipped.**
 *
 * Renders the live bus state and dispatches real commands against
 * [FakeVehicleBus], so the whole bus layer is exercised on an ordinary
 * emulator with no vehicle attached. Run it on an AVD configured
 * **1080x1920 at 160dpi** to match the target panel exactly.
 *
 * It is not a preview of screens 2a or 1d — it is a state inspector plus a
 * command keypad. Its job is to prove the seam works and to make awkward
 * states easy to reach.
 */
class HarnessActivity : ComponentActivity() {

    private val bus = FakeVehicleBus()
    private val slot = AdaptiveSlot()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Harness(bus, slot) }
    }
}

@Composable
private fun Harness(bus: FakeVehicleBus, slot: AdaptiveSlot) {
    val state by bus.state.collectAsState()
    val connected by bus.connected.collectAsState()
    val palette = Palette.forNight(state.isNight)

    // Drive the adaptive slot from a scrubbable temperature so its hysteresis
    // and dwell can be watched without waiting on weather.
    //
    // slot.update() mutates the state machine, so it must NOT be called during
    // composition — recomposition would advance it unpredictably. It is driven
    // from the button handlers below and its result held in Compose state.
    var outsideF by remember { mutableStateOf<Int?>(null) }
    var clock by remember { mutableStateOf(0L) }
    var slotContent by remember { mutableStateOf(slot.content) }

    fun scrub(toF: Int?) {
        outsideF = toF
        clock += 60_000L                      // step past the dwell window
        slotContent = slot.update(toF, clock)
    }

    var showBar by remember { mutableStateOf(false) }
    var showPanel by remember { mutableStateOf(false) }

    if (showPanel) {
        // The surface behind the INSPECTOR key: without it the strip is the
        // window background, which stays light in the night palette.
        Column(Modifier.fillMaxSize().background(palette.surface)) {
            Key("INSPECTOR", palette) { showPanel = false }
            ClimatePanel(
                state = state,
                outsideF = outsideF,
                onCommand = { bus.send(it) },
                onClose = { showPanel = false },
            )
        }
        return
    }

    if (showBar) {
        Column(
            Modifier.fillMaxSize().background(palette.surface),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Key("INSPECTOR", palette) { showBar = false }
            ClimateBar(
                state = state,
                slot = slotContent,
                onCommand = { bus.send(it) },
                onHome = {},
                onBack = {},
                // The harness shows the bar and the panel as separate views, so
                // there is no panel over this bar to toggle: the caret stays up.
                panelOpen = false,
                onToggleClimate = {},
                onSlotPressChange = { down ->
                    if (down) slot.onFingerDown() else slot.onFingerUp()
                },
            )
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(palette.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Mono("WK2 HARNESS — fake bus", palette.ink, 16.sp)
        Mono(
            if (connected) "bus: connected" else "bus: DISCONNECTED",
            if (connected) palette.accent else palette.warm,
        )

        LazyColumn(
            Modifier.fillMaxWidth().height(300.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item { Mono("--- derived ---", palette.inkMuted) }
            item { Mono("tempLeft    ${state.tempLeft}", palette.ink) }
            item { Mono("tempRight   ${state.tempRight}", palette.ink) }
            item { Mono("fan         ${state.fan}", palette.ink) }
            item { Mono("airflow     ${state.airflow}", palette.ink) }
            item { Mono("seatHeatL   ${state.seatHeatL}", palette.ink) }
            item { Mono("seatHeatR   ${state.seatHeatR}", palette.ink) }
            item { Mono("seatVentL   ${state.seatVentL}", palette.ink) }
            item { Mono("ac/auto     ${state.acOn} / ${state.autoOn}", palette.ink) }
            item { Mono("recirc/max  ${state.recircOn} / ${state.maxAcOn}", palette.ink) }
            item { Mono("sync/wheel  ${state.syncOn} / ${state.wheelHeatOn}", palette.ink) }
            item { Mono("power       ${state.powerOn}", palette.ink) }
            item { Mono("volume      ${state.volume}", palette.ink) }
            item { Mono("night       ${state.isNight}", palette.ink) }
            item {
                Mono(
                    "climateData ${state.hasClimateData}",
                    if (state.hasClimateData) palette.ink else palette.warm,
                )
            }
            item { Mono("refreshes   ${bus.refreshes}", palette.ink) }
            item { Mono("--- adaptive slot ---", palette.inkMuted) }
            item { Mono("outside     ${outsideF ?: "undecoded (null)"}", palette.ink) }
            item { Mono("slot holds  $slotContent", palette.accent) }
        }

        Mono("commands", palette.inkMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("AUTO", palette) { bus.send(Command.AUTO) }
            Key("FAN+", palette) { bus.send(Command.FAN_UP) }
            Key("FAN-", palette) { bus.send(Command.FAN_DOWN) }
            Key("A/C", palette) { bus.send(Command.AC) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("T+", palette) { bus.send(Command.TEMP_L_UP) }
            Key("T-", palette) { bus.send(Command.TEMP_L_DOWN) }
            Key("SEAT L", palette) { bus.send(Command.SEAT_HEAT_L) }
            Key("MAX", palette) { bus.send(Command.MAX_AC) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("DEFR", palette) { bus.send(Command.FRONT_DEFROST) }
            Key("FACE", palette) { bus.send(Command.AIRFLOW_FACE) }
            Key("FEET", palette) { bus.send(Command.AIRFLOW_FEET) }
            Key("SYNC", palette) { bus.send(Command.SYNC) }
        }

        Mono("inject awkward states", palette.inkMuted)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("LO", palette) { bus.inject(Signal.TEMP_LEFT, -2) }
            Key("NIGHT", palette) {
                bus.inject(Signal.ILLUMINATION, if (state.isNight == true) 0 else 1)
            }
            Key("DROP", palette) { bus.setConnected(!connected) }
            Key("BAR", palette) { showBar = true }
            Key("PANEL", palette) { showPanel = true }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // The cold-start case the emulator cannot otherwise reach: a bound
            // bus that has reported no climate signal at all. BLANK drops the
            // whole state map so screen 2a can be seen rendering indeterminate;
            // BASE puts the measured vehicle baseline back.
            Key("BLANK", palette) { bus.replace(ClimateState.EMPTY) }
            Key("BASE", palette) { bus.replace(FakeVehicleBus.VEHICLE_BASELINE) }
            Key("REFRESH", palette) { bus.refresh() }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Key("COLD", palette) { scrub(20) }
            Key("MILD", palette) { scrub(60) }
            Key("HOT", palette) { scrub(95) }
            Key("NULL", palette) { scrub(null) }
        }
    }
}

/**
 * Uses [BasicText] rather than Material's `Text`. Reaching for `Text` out of
 * habit is the single most likely way this project accidentally acquires the
 * Material dependency the design does not want — there are no Material
 * components in either screen, no ripple, and no shadows.
 */
@Composable
private fun Mono(
    text: String,
    color: Color,
    size: TextUnit = 13.sp,
) {
    BasicText(
        text = text,
        style = TextStyle(
            color = color,
            fontSize = size,
            fontFamily = FontFamily.Monospace,
        ),
    )
}

/**
 * A harness key. Uses the design's pressed-state rule deliberately: feedback
 * on touch-down with no ripple, because there is no hover on the target device
 * and ripples read as smudges on a glossy panel in sunlight.
 */
@Composable
private fun Key(label: String, palette: Palette, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .height(56.dp)
            .background(
                if (pressed) palette.accent else palette.surfaceRaised,
                RoundedCornerShape(Dimens.radiusTile),
            )
            .border(
                1.5.dp,
                palette.borderControl,
                RoundedCornerShape(Dimens.radiusTile),
            )
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicText(
            text = label,
            style = TextStyle(
                color = if (pressed) palette.accentInk else palette.ink,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
    }
}

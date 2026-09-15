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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.FakeVehicleBus
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.bus.Signal
import com.wk2.climate.design.Dimens
import com.wk2.climate.design.Palette
import com.wk2.climate.ui.bar.ClimateBar
import com.wk2.climate.ui.bar.SeatButton
import com.wk2.climate.ui.bar.SeatIndicator
import com.wk2.climate.ui.bar.SeatMenu
import com.wk2.climate.ui.bar.seatMenuX
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Harness(bus) }
    }
}

@Composable
private fun Harness(bus: FakeVehicleBus) {
    val state by bus.state.collectAsState()
    val connected by bus.connected.collectAsState()
    val palette = Palette.forNight(state.isNight)

    // Screen 1d's header shows the outside temperature; OUT below cycles it
    // through the values that used to matter, plus null for "not decoded".
    var outsideF by remember { mutableStateOf<Int?>(null) }

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

    // The harness has no second window, so the menu is drawn inline, directly
    // above the bar at the x the service would place its window. Re-tapping
    // the same button closes it; the other button switches. The service's
    // outside-touch dismissal has no equivalent here.
    var menuSide by remember { mutableStateOf<SeatSide?>(null) }
    if (showBar) {
        Column(
            Modifier.fillMaxSize().background(palette.surface),
            verticalArrangement = Arrangement.Bottom,
        ) {
            Key("INSPECTOR", palette) { showBar = false; menuSide = null }
            menuSide?.let { side ->
                Row {
                    Spacer(Modifier.width(seatMenuX(side)))
                    SeatMenu(palette = palette, side = side, state = state, onCommand = { bus.send(it) })
                }
            }
            ClimateBar(
                state = state,
                onCommand = { bus.send(it) },
                onHome = {},
                onBack = {},
                // The harness shows the bar and the panel as separate views, so
                // there is no panel over this bar to toggle: the caret stays up.
                panelOpen = false,
                onToggleClimate = { menuSide = null },
                seatMenuOpen = menuSide,
                onSeatButton = { side -> menuSide = if (menuSide == side) null else side },
            )
        }
        return
    }

    var showSeats by remember { mutableStateOf(false) }
    if (showSeats) {
        // Every state a seat button can be in, both sides, on the current
        // palette. NIGHT toggles the palette from the inspector. The first
        // driver button also shows the wheel badge; the last on each row is
        // painted as "menu open".
        val samples = listOf(
            "heat LOW" to SeatIndicator(SeatIndicator.Kind.HEAT, 1),
            "heat HIGH" to SeatIndicator(SeatIndicator.Kind.HEAT, 2),
            "cool LOW" to SeatIndicator(SeatIndicator.Kind.COOL, 1),
            "cool HIGH" to SeatIndicator(SeatIndicator.Kind.COOL, 2),
            "off" to SeatIndicator(SeatIndicator.Kind.OFF, 0),
            "unknown" to SeatIndicator(SeatIndicator.Kind.UNKNOWN, 0),
        )
        Column(
            Modifier.fillMaxSize().background(palette.surface).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Key("INSPECTOR", palette) { showSeats = false }
            for (side in SeatSide.entries) {
                Mono(side.name, palette.inkMuted)
                Row(Modifier.height(Dimens.barTopRow)) {
                    samples.forEachIndexed { i, (_, indicator) ->
                        SeatButton(
                            palette = palette,
                            side = side,
                            indicator = indicator,
                            wheelOn = side == SeatSide.DRIVER && i == 0,
                            menuOpen = i == samples.lastIndex,
                            onClick = {},
                        )
                    }
                }
                Row { samples.forEach { (name, _) -> Box(Modifier.width(Dimens.seatButton)) { Mono(name, palette.inkMuted, 11.sp) } } }
            }
            Mono("menus, live off the fake bus", palette.inkMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SeatMenu(palette = palette, side = SeatSide.DRIVER, state = state, onCommand = { bus.send(it) })
                SeatMenu(palette = palette, side = SeatSide.PASSENGER, state = state, onCommand = { bus.send(it) })
            }
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
            item { Mono("seatVentR   ${state.seatVentR}", palette.ink) }
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
            Key("SEATS", palette) { showSeats = true }
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
            Key("OUT ${outsideF ?: "—"}", palette) {
                outsideF = when (outsideF) { null -> 20; 20 -> 95; else -> null }
            }
            Key("SEAT R", palette) { bus.send(Command.SEAT_HEAT_R) }
            Key("VENT L", palette) { bus.send(Command.SEAT_VENT_L) }
            Key("WHEEL", palette) { bus.send(Command.WHEEL_HEAT) }
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

package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.design.Palette

/**
 * Screen 1d: every climate function in the vehicle, each one tap away,
 * nothing nested. 1080 x 1693, which is exactly the inset-reduced app area, so
 * it fills that region with the 227dp bar still visible below it.
 *
 * Rendered entirely from [state]. Every tap dispatches a [Command] and mutates
 * nothing locally, which is what makes the protocol's side effects — the
 * macros forcing recirculation on, AUTO clearing when the fan moves — appear
 * correctly with no special-casing.
 */
@Composable
fun ClimatePanel(
    state: ClimateState,
    outsideF: Int?,
    onCommand: (Command) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = Palette.forNight(state.isNight)

    // One gate, read straight off bus state, exactly as ClimateBar does it.
    // Absence is whole-module here — CANBUS silent — so per-flag nullability
    // would carry no more information.
    //
    // Only the two sections that read collapsing booleans need it. The rest
    // are honest by construction: the zones and the fan model absence as
    // Temp.Unavailable / Fan.Unavailable, the seat tiles as
    // SeatLevel.UNAVAILABLE, AirflowMode has explicit NONE/UNKNOWN states that
    // light nothing, and the header hides its status line on a null reading.
    val live = state.hasClimateData

    Column(
        modifier
            .fillMaxSize()
            .background(palette.surface)
            // The handoff's section heights sum to 1664 of the 1693 available, so
            // this fits without scrolling on the target panel. The scroll is a
            // safety net for a different density, never the intended interaction.
            .verticalScroll(rememberScrollState()),
    ) {
        PanelHeader(palette = palette, outsideF = outsideF, onClose = onClose)

        // Zero padding: each zone supplies the page gutter itself. See
        // PanelSection's contentPadding doc.
        PanelSection(
            palette = palette,
            header = null,
            topBorder = true,
            contentPadding = PaddingValues(0.dp),
        ) {
            PanelZones(
                palette = palette,
                driver = state.tempLeft,
                passenger = state.tempRight,
                onDriverDown = { onCommand(Command.TEMP_L_DOWN) },
                onDriverUp = { onCommand(Command.TEMP_L_UP) },
                onPassengerDown = { onCommand(Command.TEMP_R_DOWN) },
                onPassengerUp = { onCommand(Command.TEMP_R_UP) },
            )
        }

        PanelSection(palette = palette, header = null) {
            PanelFan(
                palette = palette,
                fan = state.fan,
                onDown = { onCommand(Command.FAN_DOWN) },
                onUp = { onCommand(Command.FAN_UP) },
            )
        }

        PanelSection(palette = palette, header = "AIRFLOW \u2014 FOUR MODES, DIRECT") {
            PanelAirflow(palette = palette, mode = state.airflow, onSelect = onCommand)
        }

        PanelSection(palette = palette, header = "MODE") {
            PanelMode(palette = palette, state = state, live = live, onCommand = onCommand)
        }

        PanelSection(palette = palette, header = "COMFORT") {
            PanelComfort(palette = palette, state = state, live = live, onCommand = onCommand)
        }

        PanelFooter(palette = palette, onPowerOff = { onCommand(Command.CLIMATE_POWER) })
    }
}

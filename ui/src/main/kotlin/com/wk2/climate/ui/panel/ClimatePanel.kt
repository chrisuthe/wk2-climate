package com.wk2.climate.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.ClimateState
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.Features
import com.wk2.climate.bus.Temp
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
 *
 * [features] decides which sections and tiles exist at all (see
 * [panelSections]): what the car lacks is omitted and the rest closes up. For
 * the WK2 with every answer yes that is every section, exactly as before.
 *
 * A car with rear climate (see [panelPages]) gets a FRONT | REAR toggle in
 * the header. FRONT is the page above, unchanged; REAR drives the rear zone.
 * The page lives in this composition, which [ClimatePanel]'s host creates per
 * open, so the window always opens on FRONT.
 */
@Composable
fun ClimatePanel(
    state: ClimateState,
    features: Features,
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
    // It does two things, and both matter. Nothing is *lit* from a flag whose
    // `false` only means "the vehicle has said nothing" — and the ink of every
    // control that makes a claim about the climate system drops to muted, so
    // the page reads as not-yet-live rather than as a working display of
    // zeros. Lighting nothing on its own is not enough: an unlit outlined tile
    // at full-strength ink is exactly what a tile we *know* to be off looks
    // like, which is the bug the owner reported against the bar.
    //
    // This is a rendering policy applied to a known-absent reading. Nothing
    // here synthesises a value — the sections that model absence honestly
    // (Temp.Unavailable, Fan.Unavailable, SeatLevel.UNAVAILABLE,
    // AirflowMode.UNKNOWN) keep doing so, and `live` only changes how loudly
    // that absence is drawn.
    //
    // Deliberately *not* gated: the CLOSE button, which must stay fully
    // legible so the driver can always leave the page; the footer and its
    // HOLD · OFF control; the section headers and zone labels; and the header's
    // OUT reading, which arrives on MAIN rather than CANBUS and so is live
    // even while the climate module is silent. None of them asserts anything
    // about the climate system. PanelFan needs no gate either: its unavailable
    // readout is already drawn in inkFaint.
    val live = state.hasClimateData
    val sections = panelSections(features)
    val pages = panelPages(features)
    var chosen by remember { mutableStateOf(PanelPage.FRONT) }
    // A page the car no longer has (rear switched off while open) falls back.
    val page = if (chosen in pages) chosen else PanelPage.FRONT

    // BoxWithConstraints, not a bare Column, purely to learn the viewport
    // height: `heightIn(min = maxHeight)` is what makes the footer's
    // `margin-top: auto` implementable inside a scroll. See the Spacer below.
    BoxWithConstraints(modifier.fillMaxSize().background(palette.surface)) {
        val viewportHeight = maxHeight

        Column(
            Modifier
                // The scroll is a safety net for a density where the sections
                // do not fit, never the intended interaction. On the target
                // panel they do fit: the handoff's own section heights sum to
                // 1664px of the 1693px page (README:149), and this
                // implementation measures a little shorter than that — ~1604dp
                // by uiautomator. Two different quantities: 1664 is the spec
                // figure, 1604dp is what this page actually builds to. Neither
                // is used as a layout input; the slack goes to the Spacer.
                .verticalScroll(rememberScrollState())
                // The floor is applied *inside* the scroll, which is the whole
                // trick. A vertical scroll hands its child an unbounded
                // maxHeight, so `fillMaxHeight` or a `weight` under a zero
                // minHeight would collapse to nothing — the trap this codebase
                // keeps falling into. `heightIn` never reads maxHeight: it
                // raises the *minimum* to the viewport, and Column resolves
                // weights against minHeight when maxHeight is infinite. So the
                // Spacer takes exactly the unused page, and when the content
                // is taller than the viewport the leftover is negative, the
                // Spacer measures zero, and the page scrolls as before.
                .heightIn(min = viewportHeight),
        ) {
            PanelHeader(
                palette = palette,
                outsideF = outsideF,
                onClose = onClose,
                pages = pages,
                page = page,
                onPage = { chosen = it },
            )

            if (page == PanelPage.REAR) {
                RearPage(state = state, features = features, live = live, palette = palette, onCommand = onCommand)
                return@Column
            }

            // Zero padding: each zone supplies the page gutter itself. See
            // PanelSection's contentPadding doc.
            if (PanelSectionId.ZONES in sections) {
                PanelSection(
                    palette = palette,
                    header = null,
                    topBorder = true,
                    contentPadding = PaddingValues(0.dp),
                ) {
                    PanelZones(
                        palette = palette,
                        live = live,
                        dualZone = features.dualZone,
                        driver = state.tempLeft,
                        passenger = state.tempRight,
                        onDriverDown = { onCommand(Command.TEMP_L_DOWN) },
                        onDriverUp = { onCommand(Command.TEMP_L_UP) },
                        onPassengerDown = { onCommand(Command.TEMP_R_DOWN) },
                        onPassengerUp = { onCommand(Command.TEMP_R_UP) },
                    )
                }
            }

            if (PanelSectionId.FAN in sections) {
                PanelSection(palette = palette, header = null) {
                    PanelFan(
                        palette = palette,
                        fan = state.fan,
                        hasAuto = features.has(Command.AUTO),
                        onDown = { onCommand(Command.FAN_DOWN) },
                        onUp = { onCommand(Command.FAN_UP) },
                    )
                }
            }

            if (PanelSectionId.AIRFLOW in sections) {
                PanelSection(palette = palette, header = "AIRFLOW \u2014 FOUR MODES, DIRECT") {
                    PanelAirflow(
                        palette = palette,
                        live = live,
                        mode = state.airflow,
                        onSelect = onCommand,
                    )
                }
            }

            if (PanelSectionId.MODE in sections) {
                PanelSection(palette = palette, header = "MODE") {
                    PanelMode(palette = palette, state = state, live = live, features = features, onCommand = onCommand)
                }
            }

            if (PanelSectionId.COMFORT in sections) {
                PanelSection(palette = palette, header = "COMFORT") {
                    PanelComfort(palette = palette, state = state, live = live, features = features, onCommand = onCommand)
                }
            }

            // The handoff's `margin-top: auto` on the footer. The destructive
            // control is fenced at the bottom edge of the page rather than
            // floating under COMFORT with bare surface below it.
            Spacer(Modifier.weight(1f))

            if (PanelSectionId.FOOTER in sections) {
                PanelFooter(palette = palette, onPowerOff = { onCommand(Command.CLIMATE_POWER) })
            }
        }
    }
}

/**
 * The REAR page: the rear setpoint, the rear fan, three rear airflow tiles,
 * and REAR ON / REAR AUTO / LOCK REAR. Built from the FRONT page's own
 * sections so it reads, and gates on [live], exactly the same way.
 *
 * No footer: HOLD · OFF is the front system's power, and the rear has its
 * own REAR ON tile.
 */
@Composable
private fun RearPage(
    state: ClimateState,
    features: Features,
    live: Boolean,
    palette: Palette,
    onCommand: (Command) -> Unit,
) {
    PanelSection(palette = palette, header = null, topBorder = true, contentPadding = PaddingValues(0.dp)) {
        PanelZones(
            palette = palette,
            live = live,
            dualZone = false,
            driver = state.rearTemp,
            passenger = Temp.Unavailable,
            onDriverDown = { onCommand(Command.REAR_TEMP_DOWN) },
            onDriverUp = { onCommand(Command.REAR_TEMP_UP) },
            onPassengerDown = {},
            onPassengerUp = {},
            singleLabel = "REAR",
        )
    }

    PanelSection(palette = palette, header = null) {
        PanelFan(
            palette = palette,
            fan = state.rearFan,
            hasAuto = features.has(Command.REAR_AUTO),
            onDown = { onCommand(Command.REAR_FAN_DOWN) },
            onUp = { onCommand(Command.REAR_FAN_UP) },
        )
    }

    PanelSection(palette = palette, header = "REAR AIRFLOW") {
        PanelAirflow(
            palette = palette,
            live = live,
            mode = state.rearAirflow,
            onSelect = onCommand,
            modes = rearAirflowModes,
            commandFor = { it.rearCommand },
        )
    }

    val rows = rearModeRows(features)
    if (rows.isNotEmpty()) {
        PanelSection(palette = palette, header = "REAR") {
            PanelMode(palette = palette, state = state, live = live, features = features, onCommand = onCommand, rows = rows)
        }
    }
}

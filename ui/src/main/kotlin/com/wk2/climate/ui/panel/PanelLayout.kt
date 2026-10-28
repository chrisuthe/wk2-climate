package com.wk2.climate.ui.panel

import com.wk2.climate.bus.AirflowMode
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.Features

/**
 * Screen 1d's sections, top to bottom. The header is not listed: it carries
 * only CLOSE and the outside temperature, and every car gets it.
 */
enum class PanelSectionId { ZONES, FAN, AIRFLOW, MODE, COMFORT, FOOTER }

/**
 * The sections this car gets, in page order.
 *
 * The rule is the bar's: omit what the car lacks and let the rest close up,
 * never disable or grey. ZONES is unconditional because the minimum table
 * every supported car meets includes `TEMP_L_UP` / `TEMP_L_DOWN`. FAN needs
 * both steppers -- a fan you can only turn up is not a fan control. AIRFLOW
 * needs all four direct setters (see [Features.hasAirflow]).
 */
fun panelSections(features: Features): List<PanelSectionId> = PanelSectionId.entries.filter { id ->
    when (id) {
        PanelSectionId.ZONES -> true
        PanelSectionId.FAN -> features.has(Command.FAN_UP) && features.has(Command.FAN_DOWN)
        PanelSectionId.AIRFLOW -> features.hasAirflow
        PanelSectionId.MODE -> modeRows(features).isNotEmpty()
        PanelSectionId.COMFORT -> comfortRows(features).any
        PanelSectionId.FOOTER -> features.has(Command.CLIMATE_POWER)
    }
}

/**
 * The mode grid's rows: today's two rows with absent tiles dropped, and a row
 * left empty dropped with them. The remaining tiles in a row share its width
 * evenly, so a missing tile never leaves a hole a finger could land in.
 *
 * SYNC needs no special case here: [Features.has] already refuses it on a
 * single-zone car.
 */
fun modeRows(features: Features): List<List<Command>> =
    MODE_ROWS.map { row -> row.filter { features.has(it) } }.filter { it.isNotEmpty() }

private val MODE_ROWS = listOf(
    listOf(Command.AUTO, Command.AC, Command.RECIRC, Command.MAX_AC),
    listOf(Command.FRONT_DEFROST, Command.REAR_DEFROST, Command.SYNC),
)

/**
 * Which COMFORT rows the car has. A seat row is a left/right pair and needs
 * both halves: the setup question asks about the pair, and a lone tile would
 * break the row's mirror about the centre gap.
 */
data class ComfortRows(val heat: Boolean, val vent: Boolean, val wheel: Boolean) {
    /** No rows, no section. */
    val any: Boolean get() = heat || vent || wheel
}

fun comfortRows(features: Features): ComfortRows = ComfortRows(
    heat = features.has(Command.SEAT_HEAT_L) && features.has(Command.SEAT_HEAT_R),
    vent = features.has(Command.SEAT_VENT_L) && features.has(Command.SEAT_VENT_R),
    wheel = features.has(Command.WHEEL_HEAT),
)

/** The climate window's pages. FRONT is screen 1d as it has always been. */
enum class PanelPage { FRONT, REAR }

/**
 * The pages this car gets. One page means no FRONT | REAR toggle at all, so
 * a car without rear climate keeps today's header.
 */
fun panelPages(features: Features): List<PanelPage> =
    if (features.hasRear) listOf(PanelPage.FRONT, PanelPage.REAR) else listOf(PanelPage.FRONT)

/** The REAR page's tile row: REAR ON, REAR AUTO, LOCK REAR, each only if present. */
fun rearModeRows(features: Features): List<List<Command>> =
    listOf(listOf(Command.REAR_POWER, Command.REAR_AUTO, Command.REAR_LOCK).filter { features.has(it) })
        .filter { it.isNotEmpty() }

/** The rear airflow tiles. The rear has no glass setting, so three, not four. */
val rearAirflowModes: List<AirflowMode> = listOf(AirflowMode.FACE, AirflowMode.FACE_FEET, AirflowMode.FEET)

/** The rear setter for a rear airflow tile; null for the modes the rear lacks. */
val AirflowMode.rearCommand: Command?
    get() = when (this) {
        AirflowMode.FACE -> Command.REAR_FACE
        AirflowMode.FACE_FEET -> Command.REAR_FACE_FEET
        AirflowMode.FEET -> Command.REAR_FEET
        else -> null
    }

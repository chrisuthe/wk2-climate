package com.wk2.climate.ui

import com.wk2.climate.bus.Command.AC
import com.wk2.climate.bus.Command.AUTO
import com.wk2.climate.bus.Command.FRONT_DEFROST
import com.wk2.climate.bus.Command.MAX_AC
import com.wk2.climate.bus.Command.REAR_DEFROST
import com.wk2.climate.bus.Command.RECIRC
import com.wk2.climate.bus.Command.SYNC
import com.wk2.climate.bus.AirflowMode
import com.wk2.climate.bus.Command.REAR_AUTO
import com.wk2.climate.bus.Command.REAR_FACE
import com.wk2.climate.bus.Command.REAR_FACE_FEET
import com.wk2.climate.bus.Command.REAR_FEET
import com.wk2.climate.bus.Command.REAR_LOCK
import com.wk2.climate.bus.Command.REAR_POWER
import com.wk2.climate.bus.CommandTable
import com.wk2.climate.bus.Features
import com.wk2.climate.bus.Question
import com.wk2.climate.bus.VehicleCatalog
import com.wk2.climate.ui.panel.ComfortRows
import com.wk2.climate.ui.panel.PanelPage
import com.wk2.climate.ui.panel.PanelSectionId
import com.wk2.climate.ui.panel.comfortRows
import com.wk2.climate.ui.panel.modeRows
import com.wk2.climate.ui.panel.panelPages
import com.wk2.climate.ui.panel.panelSections
import com.wk2.climate.ui.panel.rearAirflowModes
import com.wk2.climate.ui.panel.rearCommand
import com.wk2.climate.ui.panel.rearModeRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Screen 1d's contents, derived from what the vehicle has.
 *
 * The WK2 case is the regression gate: every answer yes on id 2687350 must
 * give today's window, section for section and tile for tile.
 */
class PanelLayoutTest {

    private val wk2 = VehicleCatalog.bundled()[2687350]!!.table
    private fun f(heat: Boolean = true, vent: Boolean = true, wheel: Boolean = true, dual: Boolean = true, table: CommandTable = wk2) =
        Features(table, mapOf(Question.SEAT_HEAT to heat, Question.SEAT_VENT to vent, Question.WHEEL_HEAT to wheel, Question.DUAL_ZONE to dual))

    @Test fun `WK2 all yes keeps every section and today's tiles`() {
        val f = Features.allYes(wk2)
        assertEquals(PanelSectionId.entries, panelSections(f))
        assertEquals(listOf(listOf(AUTO, AC, RECIRC, MAX_AC), listOf(FRONT_DEFROST, REAR_DEFROST, SYNC)), modeRows(f))
        assertEquals(ComfortRows(heat = true, vent = true, wheel = true), comfortRows(f))
    }

    @Test fun `a KL Cherokee has no MAX A-C tile until synthesized MAX lands`() =
        assertEquals(listOf(AUTO, AC, RECIRC), modeRows(Features.allYes(VehicleCatalog.bundled()[2621814]!!.table)).first())

    @Test fun `single zone drops SYNC`() = assertFalse(SYNC in modeRows(f(dual = false)).flatten())

    @Test fun `no comfort answers drop the section`() =
        assertFalse(PanelSectionId.COMFORT in panelSections(f(heat = false, vent = false, wheel = false)))

    @Test fun `the minimal Jeep profile has zones and mode only`() = assertEquals(
        listOf(PanelSectionId.ZONES, PanelSectionId.MODE),
        panelSections(Features.allYes(VehicleCatalog.bundled()[374]!!.table)))

    // ---- the REAR page ----

    private val front = mapOf(Question.SEAT_HEAT to true, Question.SEAT_VENT to true, Question.WHEEL_HEAT to true, Question.DUAL_ZONE to true)
    private val durango = VehicleCatalog.bundled()[1442166]!!.table

    @Test fun `a car without a rear answer has one page, so no toggle`() =
        assertEquals(listOf(PanelPage.FRONT), panelPages(Features(wk2, front)))

    @Test fun `rear yes adds the REAR page after FRONT`() =
        assertEquals(listOf(PanelPage.FRONT, PanelPage.REAR), panelPages(Features(durango, front + (Question.REAR_CLIMATE to true))))

    @Test fun `the rear tiles are REAR ON, REAR AUTO and LOCK REAR`() {
        assertEquals(listOf(listOf(REAR_POWER, REAR_AUTO, REAR_LOCK)), rearModeRows(Features(durango, front + (Question.REAR_CLIMATE to true))))
        assertEquals(emptyList<List<Any>>(), rearModeRows(Features(durango, front + (Question.REAR_CLIMATE to false))))
    }

    @Test fun `rear airflow is three direct setters, no glass`() = assertEquals(
        listOf(AirflowMode.FACE to REAR_FACE, AirflowMode.FACE_FEET to REAR_FACE_FEET, AirflowMode.FEET to REAR_FEET),
        rearAirflowModes.map { it to it.rearCommand },
    )

    @Test fun `rear yes leaves the FRONT page exactly as it was`() {
        val f = Features(wk2, front + (Question.REAR_CLIMATE to true))
        assertEquals(PanelSectionId.entries, panelSections(f))
        assertEquals(listOf(listOf(AUTO, AC, RECIRC, MAX_AC), listOf(FRONT_DEFROST, REAR_DEFROST, SYNC)), modeRows(f))
    }
}

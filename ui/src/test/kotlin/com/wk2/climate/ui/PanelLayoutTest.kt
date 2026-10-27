package com.wk2.climate.ui

import com.wk2.climate.bus.Command.AC
import com.wk2.climate.bus.Command.AUTO
import com.wk2.climate.bus.Command.FRONT_DEFROST
import com.wk2.climate.bus.Command.MAX_AC
import com.wk2.climate.bus.Command.REAR_DEFROST
import com.wk2.climate.bus.Command.RECIRC
import com.wk2.climate.bus.Command.SYNC
import com.wk2.climate.bus.CommandTable
import com.wk2.climate.bus.Features
import com.wk2.climate.bus.Question
import com.wk2.climate.bus.VehicleCatalog
import com.wk2.climate.ui.panel.ComfortRows
import com.wk2.climate.ui.panel.PanelSectionId
import com.wk2.climate.ui.panel.comfortRows
import com.wk2.climate.ui.panel.modeRows
import com.wk2.climate.ui.panel.panelSections
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
}

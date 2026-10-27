package com.wk2.climate.bus

import com.wk2.climate.bus.Command.SEAT_HEAT_L
import com.wk2.climate.bus.Command.SEAT_HEAT_R
import com.wk2.climate.bus.Command.SEAT_VENT_L
import com.wk2.climate.bus.Command.SEAT_VENT_R
import com.wk2.climate.bus.Command.WHEEL_HEAT
import com.wk2.climate.bus.Question.DUAL_ZONE
import com.wk2.climate.bus.Question.SEAT_HEAT
import com.wk2.climate.bus.Question.SEAT_VENT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FeaturesTest {

    private val wk2 = VehicleCatalog.bundled()[2687350]!!.table

    @Test
    fun `the WK2 asks all four questions`() = assertEquals(Question.entries, questionsFor(wk2))

    @Test
    fun `a table without seat cooling never asks about it`() =
        assertEquals(listOf(DUAL_ZONE), questionsFor(VehicleCatalog.bundled()[374]!!.table))

    @Test
    fun `a yes answer cannot add a command the table lacks`() =
        assertFalse(Features(CommandTable(mapOf(Command.AC to 1)), mapOf(SEAT_VENT to true)).has(SEAT_VENT_L))

    @Test
    fun `a no answer removes the commands it covers`() =
        assertFalse(Features(wk2, Question.entries.associateWith { it != SEAT_VENT }).has(SEAT_VENT_R))

    @Test
    fun `single zone removes the passenger temperature and SYNC`() {
        val f = Features(wk2, Question.entries.associateWith { it != DUAL_ZONE })
        assertFalse(f.dualZone)
        assertFalse(f.has(Command.TEMP_R_UP))
        assertFalse(f.has(Command.SYNC))
    }

    @Test
    fun `WK2 all-yes keeps today's seat menus`() {
        val f = Features.allYes(wk2)
        assertEquals(SeatSlot.Menu(listOf(SEAT_HEAT_L, SEAT_VENT_L, WHEEL_HEAT)), f.seatSlot(SeatSide.DRIVER))
        assertEquals(SeatSlot.Menu(listOf(SEAT_HEAT_R, SEAT_VENT_R)), f.seatSlot(SeatSide.PASSENGER))
    }

    @Test
    fun `one control on a side is a direct tap`() {
        val f = Features(wk2, mapOf(SEAT_HEAT to true, SEAT_VENT to false, Question.WHEEL_HEAT to true, DUAL_ZONE to true))
        assertEquals(SeatSlot.Menu(listOf(SEAT_HEAT_L, WHEEL_HEAT)), f.seatSlot(SeatSide.DRIVER))
        assertEquals(SeatSlot.Direct(SEAT_HEAT_R), f.seatSlot(SeatSide.PASSENGER))
    }

    @Test
    fun `wheel only is a direct wheel tap on the driver side and nothing on the passenger side`() {
        val f = Features(wk2, mapOf(SEAT_HEAT to false, SEAT_VENT to false, Question.WHEEL_HEAT to true, DUAL_ZONE to true))
        assertEquals(SeatSlot.Direct(WHEEL_HEAT), f.seatSlot(SeatSide.DRIVER))
        assertEquals(SeatSlot.None, f.seatSlot(SeatSide.PASSENGER))
    }

    @Test
    fun `commands no question covers follow the table`() =
        assertTrue(Features(wk2, emptyMap()).has(Command.REAR_DEFROST))

    @Test
    fun `an unanswered question keeps its commands out`() =
        assertFalse(Features(wk2, emptyMap()).has(SEAT_HEAT_L))

    @Test
    fun `the WK2 has direct airflow and the minimal Jeep profile does not`() {
        assertTrue(Features.allYes(wk2).hasAirflow)
        assertFalse(Features.allYes(VehicleCatalog.bundled()[374]!!.table).hasAirflow)
    }
}

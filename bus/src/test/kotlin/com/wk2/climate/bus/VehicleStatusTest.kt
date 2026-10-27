package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleStatusTest {

    private val catalog = VehicleCatalog.bundled()
    private val allYes = Question.entries.associateWith { true }

    @Test
    fun `no id is Waiting`() = assertEquals(VehicleStatus.Waiting, resolveVehicle(SavedVehicle.NONE, catalog))

    @Test
    fun `an id outside the catalog is Unknown`() =
        assertEquals(VehicleStatus.Unknown(1234567), resolveVehicle(SavedVehicle(1234567, emptyMap()), catalog))

    @Test
    fun `cached id is used before any live id`() =
        assertTrue(resolveVehicle(SavedVehicle(2687350, allYes), catalog) is VehicleStatus.Ready)

    @Test
    fun `the same live id keeps the answers`() =
        assertEquals(allYes, SavedVehicle(2687350, allYes).onLiveId(2687350).answers)

    @Test
    fun `a different live id discards the answers`() =
        assertEquals(SavedVehicle(2621814, emptyMap()), SavedVehicle(2687350, allYes).onLiveId(2621814))

    @Test
    fun `the first live id is adopted`() =
        assertEquals(SavedVehicle(2687350, emptyMap()), SavedVehicle.NONE.onLiveId(2687350))

    @Test
    fun `partial answers are not Ready`() {
        val s = resolveVehicle(SavedVehicle(2687350, mapOf(Question.SEAT_HEAT to true, Question.SEAT_VENT to false)), catalog)
        assertTrue(s is VehicleStatus.NeedsSetup)
        assertEquals(Question.WHEEL_HEAT, (s as VehicleStatus.NeedsSetup).questions.first { it !in s.answers })
    }

    @Test
    fun `Ready carries the answered features`() {
        val s = resolveVehicle(SavedVehicle(2687350, allYes), catalog) as VehicleStatus.Ready
        assertEquals(Features(catalog[2687350]!!.table, allYes), s.features)
    }

    @Test
    fun `a car with no questions is Ready at once`() {
        val tiny = VehicleCatalog.parse("P\tX\tAC=1,TEMP_L_UP=4,TEMP_L_DOWN=5\nV\t9\tX\t-\t-\n")
        assertTrue(resolveVehicle(SavedVehicle(9, emptyMap()), tiny) is VehicleStatus.Ready)
    }
}

package com.wk2.climate.bus

import com.wk2.climate.bus.Command.AC
import com.wk2.climate.bus.Command.AIRFLOW_FACE
import com.wk2.climate.bus.Command.AIRFLOW_FACE_FEET
import com.wk2.climate.bus.Command.AIRFLOW_FEET
import com.wk2.climate.bus.Command.AIRFLOW_FEET_GLASS
import com.wk2.climate.bus.Command.AUTO
import com.wk2.climate.bus.Command.CLIMATE_POWER
import com.wk2.climate.bus.Command.FAN_DOWN
import com.wk2.climate.bus.Command.FAN_UP
import com.wk2.climate.bus.Command.FRONT_DEFROST
import com.wk2.climate.bus.Command.MAX_AC
import com.wk2.climate.bus.Command.REAR_DEFROST
import com.wk2.climate.bus.Command.RECIRC
import com.wk2.climate.bus.Command.SEAT_HEAT_L
import com.wk2.climate.bus.Command.SEAT_HEAT_R
import com.wk2.climate.bus.Command.SEAT_VENT_L
import com.wk2.climate.bus.Command.SEAT_VENT_R
import com.wk2.climate.bus.Command.SYNC
import com.wk2.climate.bus.Command.TEMP_L_DOWN
import com.wk2.climate.bus.Command.TEMP_L_UP
import com.wk2.climate.bus.Command.TEMP_R_DOWN
import com.wk2.climate.bus.Command.TEMP_R_UP
import com.wk2.climate.bus.Command.WHEEL_HEAT
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Against the bundled `vehicles.tsv`, which `tools/extract-vehicles.py`
 * generates from the `com.syu.air` APK. The WK2 test is the regression gate:
 * the extracted table must equal the one swept by hand on the vehicle.
 */
class VehicleCatalogTest {

    private val catalog = VehicleCatalog.bundled()

    @Test
    fun `the WK2 table is the hand-swept one`() {
        val table = catalog[2687350]!!.table
        assertEquals(
            mapOf(
                AC to 1, AUTO to 2, RECIRC to 3, TEMP_L_UP to 4, TEMP_L_DOWN to 5, FAN_UP to 6, FAN_DOWN to 7,
                AIRFLOW_FACE to 8, AIRFLOW_FACE_FEET to 9, AIRFLOW_FEET to 10, AIRFLOW_FEET_GLASS to 11,
                FRONT_DEFROST to 12, SYNC to 13, REAR_DEFROST to 14, MAX_AC to 15, CLIMATE_POWER to 16,
                SEAT_HEAT_L to 17, SEAT_HEAT_R to 18, TEMP_R_UP to 20, TEMP_R_DOWN to 21,
                SEAT_VENT_L to 22, SEAT_VENT_R to 23, WHEEL_HEAT to 24,
            ),
            table.commands.associateWith { table[it]!! },
        )
    }

    @Test
    fun `the amplified Grand Cherokee also gets native MAX A-C`() =
        assertEquals(15, catalog[5308790]!!.table[MAX_AC])

    @Test
    fun `a KL Cherokee shares the class but not index 15`() =
        assertNull(catalog[2621814]!!.table[MAX_AC])

    @Test
    fun `the bundled catalog is exactly the PA family`() = assertEquals(60, catalog.ids.size)

    @Test
    fun `an unknown id is not in the catalog`() = assertNull(catalog[1234567])

    @Test
    fun `index 19 is never exposed`() =
        assertTrue(catalog.ids.all { id -> catalog[id]!!.table.let { t -> t.commands.none { t[it] == 19 } } })

    @Test
    fun `display names`() {
        assertEquals("Grand Cherokee 2014–2022", vehicleDisplayName("CAR_PA_GrandCherokee_14_22", "x"))
        assertEquals(
            "Grand Cherokee 2014–2022, amplified audio",
            vehicleDisplayName("CAR_PA_GrandCherokee_14_22_Amp", "x"),
        )
        assertEquals("RAM 2013–2018, low trim", vehicleDisplayName("CAR_PA_RAM_13_18_Low", "x"))
        assertEquals("Wrangler 2018–2020, high trim", vehicleDisplayName("CAR_PA_Wrangler_18_20_H", "x"))
        assertEquals("Ford F150 2020, CD", vehicleDisplayName("CAR_PA_Ford_F150_20_CD", "x"))
        assertEquals("Car_0374_PA_Jeep_All", vehicleDisplayName(null, "Car_0374_PA_Jeep_All"))
    }

    @Test
    fun `the WK2 carries its vendor name`() =
        assertEquals("Grand Cherokee 2014–2022", catalog[2687350]!!.displayName)

    @Test(expected = IllegalArgumentException::class)
    fun `a V line for an unknown profile is refused`() {
        VehicleCatalog.parse("V\t1\tNoSuchProfile\t-\t-\n")
    }

    @Test
    fun `windows line endings parse`() =
        assertEquals(setOf(9), VehicleCatalog.parse("# c\r\nP\tX\tAC=1\r\nV\t9\tX\t-\t-\r\n").ids)
}

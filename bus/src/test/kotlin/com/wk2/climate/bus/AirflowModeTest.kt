package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AirflowModeTest {

    @Test
    fun `the four named combinations map to the four modes`() {
        assertEquals(AirflowMode.FACE, AirflowMode.from(0, 1, 0))
        assertEquals(AirflowMode.FACE_FEET, AirflowMode.from(0, 1, 1))
        assertEquals(AirflowMode.FEET, AirflowMode.from(0, 0, 1))
        assertEquals(AirflowMode.FEET_GLASS, AirflowMode.from(1, 0, 1))
    }

    @Test
    fun `all flags clear is NONE, the resting state under AUTO`() {
        // Measured on vehicle: with U_AIR_AUTO = 1 all three flags read 0.
        // This is normal, not an error -- AUTO owns airflow and the driver has
        // selected nothing, so no tile should light.
        assertEquals(AirflowMode.NONE, AirflowMode.from(0, 0, 0))
    }

    @Test
    fun `unnamed combinations are UNKNOWN`() {
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(1, 1, 1))
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(1, 1, 0))
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(1, 0, 0))
    }

    @Test
    fun `a missing flag is UNKNOWN rather than a guess`() {
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(null, 1, 0))
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(0, null, 0))
        assertEquals(AirflowMode.UNKNOWN, AirflowMode.from(0, 1, null))
    }

    @Test
    fun `only the four real modes count as driver selected`() {
        listOf(
            AirflowMode.FACE, AirflowMode.FACE_FEET,
            AirflowMode.FEET, AirflowMode.FEET_GLASS,
        ).forEach { assertTrue("$it should be driver selected", it.isDriverSelected) }

        assertFalse(AirflowMode.NONE.isDriverSelected)
        assertFalse(AirflowMode.UNKNOWN.isDriverSelected)
    }

    @Test
    fun `NONE and UNKNOWN both light no tile`() {
        // They render identically. The difference is only that UNKNOWN is worth
        // logging and NONE is not.
        assertFalse(AirflowMode.NONE.isDriverSelected)
        assertFalse(AirflowMode.UNKNOWN.isDriverSelected)
    }

    @Test
    fun `each selectable mode maps to its idempotent setter command`() {
        assertEquals(Command.AIRFLOW_FACE, AirflowMode.FACE.command)
        assertEquals(Command.AIRFLOW_FACE_FEET, AirflowMode.FACE_FEET.command)
        assertEquals(Command.AIRFLOW_FEET, AirflowMode.FEET.command)
        assertEquals(Command.AIRFLOW_FEET_GLASS, AirflowMode.FEET_GLASS.command)
    }

    @Test
    fun `NONE and UNKNOWN have no command - they are readings, not choices`() {
        assertNull(AirflowMode.NONE.command)
        assertNull(AirflowMode.UNKNOWN.command)
    }

    @Test
    fun `selectable lists exactly the four tiles in design order`() {
        assertEquals(
            listOf(
                AirflowMode.FACE,
                AirflowMode.FACE_FEET,
                AirflowMode.FEET,
                AirflowMode.FEET_GLASS,
            ),
            AirflowMode.selectable,
        )
    }
}

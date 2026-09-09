package com.wk2.climate.bus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CanbusIdTest {

    @Test
    fun `decodes this vehicle's id`() {
        // CAR_PA_Cherokee_14_22, which AirFactory maps to
        // Car_0374_PA_Jeep_Wrangler - the profile whose 18 command indices
        // match the table in Command.
        val id = 2621814
        assertEquals(374, CanbusId.base(id))
        assertEquals(40, CanbusId.variant(id))
    }

    @Test
    fun `a bare base id has variant zero`() {
        // Car_0374_PA_Jeep_All is wired to the bare 374 as well as 65910.
        assertEquals(374, CanbusId.base(374))
        assertEquals(0, CanbusId.variant(374))
        assertEquals(374, CanbusId.base(65910))
        assertEquals(1, CanbusId.variant(65910))
    }

    @Test
    fun `variant is unsigned, so a high bit does not read as negative`() {
        // ushr, not shr. The vendor's ids stay well below this, but a sign-
        // extending shift would turn a large id into a negative variant and
        // silently fail to match any table entry.
        val id = -0x10000  // 0xFFFF0000
        assertEquals(0, CanbusId.base(id))
        assertEquals(0xFFFF, CanbusId.variant(id))
    }

    @Test
    fun `describe is readable in a log`() {
        assertEquals("2621814 (0x280176) base=374 variant=40",
            CanbusId.describe(2621814))
    }

    @Test
    fun `the id is registered but gates nothing`() {
        // It must reach the vendor as a real registration, and must not become
        // a precondition for showing the bar: a vehicle that never reports it
        // has to behave exactly as before.
        val state = ClimateState.of(Signal.AC to 1)
        assertTrue(state.hasClimateData)
    }

    @Test
    fun `an unreported id does not keep the re-registration retry running`() {
        // missingSignals is the retry's stopping rule. Chasing a diagnostic
        // the vehicle may simply not have would run every start to the cap,
        // for a value nothing renders.
        assertTrue(Signal.CANBUS_ID.diagnostic)
        val everythingRendered = Signal.entries.filterNot { it.diagnostic }
            .fold(ClimateState.EMPTY) { acc, s -> acc.with(s, 0) }
        assertEquals(emptySet<Signal>(), everythingRendered.missingSignals)
    }
}

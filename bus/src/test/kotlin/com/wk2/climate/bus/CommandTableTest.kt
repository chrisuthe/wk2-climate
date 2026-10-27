package com.wk2.climate.bus

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommandTableTest {

    @Test
    fun `a climate command in the table frames as 1, index`() =
        assertArrayEquals(intArrayOf(1, 4), CommandTable(mapOf(Command.TEMP_L_UP to 4)).frame(Command.TEMP_L_UP))

    @Test
    fun `a climate command missing from the table has no frame`() =
        assertNull(CommandTable(mapOf(Command.AC to 1)).frame(Command.MAX_AC))

    @Test
    fun `no table means no frame`() = assertNull((null as CommandTable?).frame(Command.AC))

    @Test
    fun `volume frames without a table`() =
        assertArrayEquals(intArrayOf(-1), (null as CommandTable?).frame(Command.VOL_UP))

    @Test(expected = IllegalArgumentException::class)
    fun `two commands on one index are refused`() {
        CommandTable(mapOf(Command.AC to 1, Command.AUTO to 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `volume cannot be in a table`() {
        CommandTable(mapOf(Command.VOL_UP to 1))
    }
}

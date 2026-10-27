package com.wk2.climate.ui

import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.CommandTable
import com.wk2.climate.bus.Features
import com.wk2.climate.bus.Question
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.bus.VehicleCatalog
import com.wk2.climate.design.Dimens
import com.wk2.climate.ui.bar.TopCell.CLIMATE
import com.wk2.climate.ui.bar.TopCell.DRIVER_MAIN
import com.wk2.climate.ui.bar.TopCell.DRIVER_SEAT
import com.wk2.climate.ui.bar.TopCell.PASSENGER_SEAT
import com.wk2.climate.ui.bar.driverMainCommand
import com.wk2.climate.ui.bar.seatMenuHeight
import com.wk2.climate.ui.bar.seatMenuRows
import com.wk2.climate.ui.bar.seatMenuX
import com.wk2.climate.ui.bar.topRowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Screen 2a's fixed geometry, checked arithmetically.
 *
 * `BarTopRow` lays its four cells out at absolute widths inside a fixed-width
 * `Row`. Nothing in Compose complains if they overrun: the last one is simply
 * measured short or clipped, so CLIMATE would quietly lose its caret rather
 * than the build failing. These are the assertions that make retuning a cell
 * width a compile-and-test problem instead of a thing noticed in the car.
 */
class BarGeometryTest {

    private val wk2 = VehicleCatalog.bundled()[2687350]!!.table

    private fun f(
        heat: Boolean = true,
        vent: Boolean = true,
        wheel: Boolean = true,
        dual: Boolean = true,
        table: CommandTable = wk2,
    ) = Features(
        table,
        mapOf(
            Question.SEAT_HEAT to heat,
            Question.SEAT_VENT to vent,
            Question.WHEEL_HEAT to wheel,
            Question.DUAL_ZONE to dual,
        ),
    )

    private val bools = listOf(true, false)

    @Test
    fun `WK2 all yes is today's row`() = assertEquals(
        listOf(DRIVER_SEAT to 128.dp, DRIVER_MAIN to 256.dp, CLIMATE to 256.dp, PASSENGER_SEAT to 128.dp),
        topRowLayout(f()).map { it.cell to it.width },
    )

    @Test
    fun `wheel only widens CLIMATE to 384`() = assertEquals(
        listOf(DRIVER_SEAT to 128.dp, DRIVER_MAIN to 256.dp, CLIMATE to 384.dp),
        topRowLayout(f(heat = false, vent = false)).map { it.cell to it.width },
    )

    @Test
    fun `no comfort is two halves`() = assertEquals(
        listOf(DRIVER_MAIN to 384.dp, CLIMATE to 384.dp),
        topRowLayout(f(heat = false, vent = false, wheel = false)).map { it.cell to it.width },
    )

    @Test
    fun `without AUTO the driver main cell is A-C`() =
        assertEquals(Command.AC, driverMainCommand(Features.allYes(VehicleCatalog.bundled()[374]!!.table)))

    @Test
    fun `with neither AUTO nor A-C, CLIMATE absorbs the driver half's main cell`() {
        val table = CommandTable(mapOf(Command.TEMP_L_UP to 4, Command.TEMP_L_DOWN to 5, Command.FAN_UP to 6, Command.FAN_DOWN to 7))
        assertEquals(listOf(CLIMATE to 768.dp), topRowLayout(Features.allYes(table)).map { it.cell to it.width })
    }

    @Test
    fun `every derived row fills the centre column and clears the floor`() {
        for (h in bools) for (v in bools) for (w in bools) {
            val row = topRowLayout(f(h, v, w))
            assertEquals(Dimens.barCenterColumn, row.fold(0.dp) { a, c -> a + c.width })
            for (cell in row) {
                assertTrue("${cell.cell} is ${cell.width.value}dp, below the floor", cell.width >= Dimens.minTarget)
            }
        }
        assertTrue(
            "the top row is ${Dimens.barTopRow.value}dp tall, below the floor",
            Dimens.barTopRow >= Dimens.minTarget,
        )
    }

    @Test
    fun `the centre line never moves when both halves have a main cell`() {
        for (h in bools) for (w in bools) {
            val row = topRowLayout(f(heat = h, vent = false, wheel = w))
            val driverHalf = row.takeWhile { it.cell != CLIMATE && it.cell != PASSENGER_SEAT }
                .fold(0.dp) { a, c -> a + c.width }
            assertEquals(Dimens.barHalf, driverHalf)
        }
    }

    @Test
    fun `menus have one row per control`() {
        assertEquals(3, seatMenuRows(SeatSide.DRIVER, f()))
        assertEquals(2, seatMenuRows(SeatSide.PASSENGER, f()))
        assertEquals(2, seatMenuRows(SeatSide.DRIVER, f(vent = false)))
    }

    @Test
    fun `the centre column and the two side columns fill the bar's width`() {
        assertEquals(
            Dimens.barWidth,
            Dimens.barSideColumn + Dimens.barCenterColumn + Dimens.barSideColumn,
        )
    }

    @Test
    fun `the centre column's two rows fill the bar's height`() {
        assertEquals(Dimens.barHeight, Dimens.barTopRow + Dimens.barBottomRow)
    }

    @Test
    fun `the nav column's two rows fill the bar's height`() {
        assertEquals(Dimens.barHeight, Dimens.barNavRow + Dimens.barNavRow)
    }

    @Test
    fun `each seat menu is a whole number of rows and every row clears the touch floor`() {
        assertTrue(Dimens.seatMenuRow >= Dimens.minTarget)
        for (side in SeatSide.entries) {
            assertEquals(Dimens.seatMenuRow * seatMenuRows(side, f()), seatMenuHeight(side, f()))
        }
        assertEquals(3, seatMenuRows(SeatSide.DRIVER, f()))
        assertEquals(2, seatMenuRows(SeatSide.PASSENGER, f()))
    }

    @Test
    fun `the driver menu spans 156 to 516, left-aligned to the driver button`() {
        assertEquals(156.dp, seatMenuX(SeatSide.DRIVER))
        assertEquals(516.dp, seatMenuX(SeatSide.DRIVER) + Dimens.seatMenuWidth)
    }

    @Test
    fun `the passenger menu spans 564 to 924, right-aligned to the passenger button`() {
        assertEquals(564.dp, seatMenuX(SeatSide.PASSENGER))
        assertEquals(924.dp, seatMenuX(SeatSide.PASSENGER) + Dimens.seatMenuWidth)
    }

    @Test
    fun `both menus sit inside the centre column and mirror each other about the bar's centre`() {
        val columnStart = Dimens.barSideColumn
        val columnEnd = Dimens.barSideColumn + Dimens.barCenterColumn
        for (side in SeatSide.entries) {
            assertTrue("$side menu starts left of the centre column", seatMenuX(side) >= columnStart)
            assertTrue("$side menu overruns the centre column", seatMenuX(side) + Dimens.seatMenuWidth <= columnEnd)
        }
        assertEquals(
            seatMenuX(SeatSide.DRIVER),
            Dimens.barWidth - (seatMenuX(SeatSide.PASSENGER) + Dimens.seatMenuWidth),
        )
    }

    @Test
    fun `a menu row's label sits right of its icon and its state text inside the menu`() {
        assertTrue(
            "label x must clear the icon: ${Dimens.seatMenuLabelX.value} <= " +
                "${Dimens.seatMenuIconInset.value} + ${Dimens.seatMenuIconSize.value}",
            Dimens.seatMenuLabelX > Dimens.seatMenuIconInset + Dimens.seatMenuIconSize,
        )
        assertTrue(
            "label and state inset overrun the menu width",
            Dimens.seatMenuLabelX + Dimens.seatMenuStateInset < Dimens.seatMenuWidth,
        )
    }
}

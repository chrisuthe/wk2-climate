package com.wk2.climate.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.design.Dimens
import com.wk2.climate.ui.bar.seatMenuHeight
import com.wk2.climate.ui.bar.seatMenuRows
import com.wk2.climate.ui.bar.seatMenuX
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

    /** The top row, left to right, as `BarTopRow` lays it out. */
    private val topRow: List<Pair<String, Dp>> = listOf(
        "driver seat" to Dimens.seatButton,
        "AUTO" to Dimens.slotAuto,
        "CLIMATE" to Dimens.slotClimate,
        "passenger seat" to Dimens.seatButton,
    )

    @Test
    fun `the top row's four cells fill the centre column exactly`() {
        val sum = topRow.fold(0.dp) { acc, (_, width) -> acc + width }
        assertEquals(Dimens.barCenterColumn, sum)
    }

    @Test
    fun `every top row cell clears the 96dp touch floor`() {
        for ((name, width) in topRow) {
            assertTrue(
                "$name is ${width.value}dp, below the ${Dimens.minTarget.value}dp floor",
                width >= Dimens.minTarget,
            )
        }
        assertTrue(
            "the top row is ${Dimens.barTopRow.value}dp tall, below the floor",
            Dimens.barTopRow >= Dimens.minTarget,
        )
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
    fun `AUTO and CLIMATE meet on the centre divider, so the row mirrors the zones beneath it`() {
        assertEquals(Dimens.barCenterColumn / 2, Dimens.seatButton + Dimens.slotAuto)
    }

    @Test
    fun `each seat menu is a whole number of rows and every row clears the touch floor`() {
        assertTrue(Dimens.seatMenuRow >= Dimens.minTarget)
        for (side in SeatSide.entries) {
            assertEquals(Dimens.seatMenuRow * seatMenuRows(side), seatMenuHeight(side))
        }
        assertEquals(3, seatMenuRows(SeatSide.DRIVER))
        assertEquals(2, seatMenuRows(SeatSide.PASSENGER))
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
}

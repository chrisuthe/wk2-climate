package com.wk2.climate.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wk2.climate.design.Dimens
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
        "AUTO" to Dimens.slotAuto,
        "first adaptive cell" to Dimens.slotAdaptiveFirst,
        "second adaptive cell" to Dimens.slotAdaptiveSecond,
        "CLIMATE" to Dimens.slotClimate,
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
}

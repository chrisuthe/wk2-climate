package com.wk2.climate.ui.bar

import androidx.compose.ui.unit.Dp
import com.wk2.climate.bus.Command
import com.wk2.climate.bus.Features
import com.wk2.climate.bus.SeatSide
import com.wk2.climate.bus.SeatSlot
import com.wk2.climate.design.Dimens

/** The cells the bar's top row can hold, left to right. */
enum class TopCell { DRIVER_SEAT, DRIVER_MAIN, CLIMATE, PASSENGER_SEAT }

data class TopRowCell(val cell: TopCell, val width: Dp)

/**
 * The top row for this car. Each half sits directly above its own
 * temperature and is [Dimens.barHalf] wide: a seat slot plus its main cell
 * (128 + 256), or the main cell alone at 384 when that side's seat slot has
 * nothing in it. The centre line therefore never moves, and for a given car
 * every cell keeps a constant coordinate and can still be found by feel.
 *
 * The driver half's main cell is AUTO, or A/C on a car without AUTO. With
 * neither -- no car in the bundled catalog, but the row must still fill the
 * column -- the driver half has no main cell and CLIMATE absorbs its width.
 */
fun topRowLayout(features: Features): List<TopRowCell> {
    val driverSeat = features.seatSlot(SeatSide.DRIVER) != SeatSlot.None
    val passengerSeat = features.seatSlot(SeatSide.PASSENGER) != SeatSlot.None
    val driverMain = driverMainCommand(features) != null

    val driverRest = if (driverSeat) Dimens.barHalf - Dimens.seatButton else Dimens.barHalf
    val passengerRest = if (passengerSeat) Dimens.barHalf - Dimens.seatButton else Dimens.barHalf

    return buildList {
        if (driverSeat) add(TopRowCell(TopCell.DRIVER_SEAT, Dimens.seatButton))
        if (driverMain) add(TopRowCell(TopCell.DRIVER_MAIN, driverRest))
        add(TopRowCell(TopCell.CLIMATE, if (driverMain) passengerRest else passengerRest + driverRest))
        if (passengerSeat) add(TopRowCell(TopCell.PASSENGER_SEAT, Dimens.seatButton))
    }
}

/** What the driver half's main cell sends: AUTO, else A/C, else nothing. */
fun driverMainCommand(features: Features): Command? = when {
    features.has(Command.AUTO) -> Command.AUTO
    features.has(Command.AC) -> Command.AC
    else -> null
}

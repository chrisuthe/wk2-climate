package com.wk2.climate.bus

/**
 * Decodes [Signal.CANBUS_ID], the value the head unit uses to say which vehicle
 * it is configured for.
 *
 * The id is a packed pair. The low 16 bits are the **base protocol** and match
 * the four-digit number in the vendor's profile class names; the high 16 bits
 * are the **vehicle variant** within that protocol. So `0x280176` is base 374,
 * variant 40 - `CAR_PA_Cherokee_14_22`, which resolves to
 * `Car_0374_PA_Jeep_Wrangler`.
 *
 * The rule holds for 1077 of the 1200 ids wired in `AirFactory`, so it is a
 * reliable way to read a log but not a substitute for the id-to-profile table
 * in `docs/data/air-canbus-ids.json`. Resolution uses the whole id.
 */
object CanbusId {

    /** The protocol family, matching the `Car_NNNN_` prefix of a profile. */
    fun base(id: Int): Int = id and 0xFFFF

    /** The vehicle variant within that family: trim level, model year, market. */
    fun variant(id: Int): Int = id ushr 16

    /** For logs: `2621814 (0x280176) base=374 variant=40`. */
    fun describe(id: Int): String =
        "$id (0x%06X) base=${base(id)} variant=${variant(id)}".format(id)
}

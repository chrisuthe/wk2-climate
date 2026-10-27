package com.wk2.climate.bus

/**
 * What is persisted about the car: the last canbus id seen and the driver's
 * setup answers for it.
 *
 * The id is cached so the bar can come up at boot from the last known car
 * without waiting for code 1000 -- which may arrive late, or not at all, on an
 * ignition-off boot where `com.syu.ms` comes up after us.
 */
data class SavedVehicle(val id: Int?, val answers: Map<Question, Boolean>) {

    /**
     * Folds in an id the head unit has just reported. The same id changes
     * nothing. A different one means the head unit's canbus setting was
     * changed, so the old answers describe some other car and are discarded:
     * setup runs again.
     */
    fun onLiveId(live: Int): SavedVehicle = if (live == id) this else SavedVehicle(live, emptyMap())

    companion object {
        val NONE = SavedVehicle(null, emptyMap())
    }
}

/** Where the app stands with the car. Only [Ready] draws a bar or sends anything. */
sealed interface VehicleStatus {

    /** No id cached and none reported yet. */
    data object Waiting : VehicleStatus

    /** An id with no trusted table. Stay out of the way; show the id so it can be reported. */
    data class Unknown(val id: Int) : VehicleStatus

    /** A known car with at least one setup question unanswered. */
    data class NeedsSetup(
        val vehicle: Vehicle,
        val questions: List<Question>,
        val answers: Map<Question, Boolean>,
    ) : VehicleStatus

    /** A known car, fully set up. */
    data class Ready(val vehicle: Vehicle, val features: Features) : VehicleStatus
}

/**
 * Turns what is persisted into a status. Ready only when every question this
 * car's table raises has an answer -- an abandoned setup stays NeedsSetup and
 * resumes at the first unanswered question.
 */
fun resolveVehicle(saved: SavedVehicle, catalog: VehicleCatalog): VehicleStatus {
    val id = saved.id ?: return VehicleStatus.Waiting
    val vehicle = catalog[id] ?: return VehicleStatus.Unknown(id)
    val questions = questionsFor(vehicle.table)
    return if (questions.all { it in saved.answers }) {
        VehicleStatus.Ready(vehicle, Features(vehicle.table, saved.answers))
    } else {
        VehicleStatus.NeedsSetup(vehicle, questions, saved.answers)
    }
}

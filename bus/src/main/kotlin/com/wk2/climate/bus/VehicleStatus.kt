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
    fun onLiveId(live: Int): SavedVehicle = when {
        // Not an id: every canbus id has a nonzero base. A transient 0 during
        // boot must not cost the driver their setup.
        live <= 0 -> this
        live == id -> this
        else -> SavedVehicle(live, emptyMap())
    }

    /**
     * Records an answer given while the setup screen showed car [forId]. If
     * the car on record has changed since -- the service recorded a new live
     * id between the screen drawing and the tap -- the answer is dropped, so a
     * stale tap can never bring the previous car's id back.
     */
    fun withAnswer(forId: Int, question: Question, yes: Boolean): SavedVehicle =
        if (forId != id) this else copy(answers = answers + (question to yes))

    /** Clears the answers to run setup again, for the car the screen showed, if it is still on record. */
    fun askAgain(forId: Int): SavedVehicle = if (forId != id) this else copy(answers = emptyMap())

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

    /** A known car, set up enough to draw the bar. */
    data class Ready(val vehicle: Vehicle, val features: Features) : VehicleStatus {

        /**
         * Questions this car raises that were never answered: only ones that
         * do not block the bar (see [Question.blocksBar]). They read as no
         * until the setup screen asks them.
         */
        val pending: List<Question> get() = questionsFor(vehicle.table).filter { it !in features.answers }
    }
}

/**
 * Turns what is persisted into a status. Ready only when every question this
 * car's table raises that blocks the bar has an answer -- an abandoned setup
 * stays NeedsSetup and resumes at the first unanswered question.
 */
fun resolveVehicle(saved: SavedVehicle, catalog: VehicleCatalog): VehicleStatus {
    val id = saved.id ?: return VehicleStatus.Waiting
    val vehicle = catalog[id] ?: return VehicleStatus.Unknown(id)
    val questions = questionsFor(vehicle.table)
    return if (questions.all { !it.blocksBar || it in saved.answers }) {
        VehicleStatus.Ready(vehicle, Features(vehicle.table, saved.answers))
    } else {
        VehicleStatus.NeedsSetup(vehicle, questions, saved.answers)
    }
}

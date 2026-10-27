package com.wk2.climate.bus

/**
 * A one-time setup question: does this car have a trim-level option its
 * profile could control?
 *
 * The command table says what we **can** send; the driver's answers say what
 * the car **has**. A question is only ever asked when the table holds every
 * command in [needs] -- there is no point asking about cooled seats on a
 * profile with no seat cooling commands.
 */
enum class Question(val needs: Set<Command>) {
    SEAT_HEAT(setOf(Command.SEAT_HEAT_L, Command.SEAT_HEAT_R)),
    SEAT_VENT(setOf(Command.SEAT_VENT_L, Command.SEAT_VENT_R)),
    WHEEL_HEAT(setOf(Command.WHEEL_HEAT)),
    DUAL_ZONE(setOf(Command.TEMP_R_UP, Command.TEMP_R_DOWN)),
}

/** The questions to ask for this table, in asking order. */
fun questionsFor(table: CommandTable): List<Question> =
    Question.entries.filter { q -> q.needs.all { it in table } }

/** What a seat button does on one side of the bar. */
sealed interface SeatSlot {
    /** Nothing on this side: the slot is omitted and its neighbour widens. */
    data object None : SeatSlot

    /** Exactly one control: a tap sends it, like a factory switch. No menu. */
    data class Direct(val command: Command) : SeatSlot

    /** Two or more controls: the seat menu, one row per control. */
    data class Menu(val rows: List<Command>) : SeatSlot
}

/**
 * What this car actually has: its [table] narrowed by the driver's [answers].
 *
 * A command is present when the table has it and, if a [Question] covers it,
 * that question was answered yes. An answer can only take away -- a yes never
 * adds a command the table lacks -- and an unanswered question keeps its
 * commands out. SYNC also needs two zones, since there is nothing to sync on
 * a single-zone car.
 */
class Features(val table: CommandTable, val answers: Map<Question, Boolean>) {

    fun has(command: Command): Boolean {
        if (command !in table) return false
        if (command == Command.SYNC && !dualZone) return false
        val question = Question.entries.firstOrNull { command in it.needs } ?: return true
        return answers[question] == true
    }

    val dualZone: Boolean get() = has(Command.TEMP_R_UP) && has(Command.TEMP_R_DOWN)

    /**
     * All four idempotent airflow setters. A car with only some of them, or
     * with a mode-cycle command instead, gets no airflow tiles: the tiles
     * promise direct selection.
     */
    val hasAirflow: Boolean get() = AIRFLOW.all { has(it) }

    fun seatSlot(side: SeatSide): SeatSlot {
        val rows = when (side) {
            SeatSide.DRIVER -> listOf(Command.SEAT_HEAT_L, Command.SEAT_VENT_L, Command.WHEEL_HEAT)
            SeatSide.PASSENGER -> listOf(Command.SEAT_HEAT_R, Command.SEAT_VENT_R)
        }.filter { has(it) }
        return when (rows.size) {
            0 -> SeatSlot.None
            1 -> SeatSlot.Direct(rows.single())
            else -> SeatSlot.Menu(rows)
        }
    }

    override fun equals(other: Any?): Boolean =
        other is Features && other.table == table && other.answers == answers

    override fun hashCode(): Int = 31 * table.hashCode() + answers.hashCode()

    override fun toString(): String = "Features(${Command.entries.filter { has(it) }})"

    companion object {
        private val AIRFLOW = listOf(
            Command.AIRFLOW_FACE, Command.AIRFLOW_FACE_FEET, Command.AIRFLOW_FEET, Command.AIRFLOW_FEET_GLASS,
        )

        /** Every question answered yes: everything the table can send. */
        fun allYes(table: CommandTable): Features = Features(table, Question.entries.associateWith { true })
    }
}

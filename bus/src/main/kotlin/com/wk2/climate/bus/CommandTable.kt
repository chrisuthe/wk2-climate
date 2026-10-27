package com.wk2.climate.bus

/**
 * One vehicle's climate command indices: which module-7 index drives which
 * [Command] on that car.
 *
 * Holds only what the vehicle's profile declared, plus additions verified on a
 * real vehicle -- see `tools/extract-vehicles.py`. A control missing here does
 * not exist for this car: [frame] returns null for it and nothing is sent.
 *
 * Two commands on one index are refused outright. A table where AC and AUTO
 * share an index is an extraction that has gone wrong, and sending through it
 * would press one control while the screen showed the other.
 */
class CommandTable(indices: Map<Command, Int>) {

    private val indices: Map<Command, Int> = indices.toMap()

    init {
        require(indices.keys.all { it.module == Signal.MODULE_CANBUS }) {
            "only climate commands carry a per-vehicle index: ${indices.keys.filter { it.module != Signal.MODULE_CANBUS }}"
        }
        require(indices.values.toSet().size == indices.size) {
            "two commands share an index: $indices"
        }
    }

    operator fun get(command: Command): Int? = indices[command]

    operator fun contains(command: Command): Boolean = command in indices

    val commands: Set<Command> get() = indices.keys

    override fun equals(other: Any?): Boolean = other is CommandTable && other.indices == indices
    override fun hashCode(): Int = indices.hashCode()
    override fun toString(): String = "CommandTable($indices)"
}

/**
 * The payload to send for [command] on this vehicle, or null if it must not be
 * sent.
 *
 * Climate: `{1, index}` -- the only frame the app speaks, and the one verified
 * on the WK2 -- when the table has the command. No table, or no entry, is null:
 * a tap that arrives before the vehicle is known is dropped, never sent with a
 * guessed index. Volume is not per-vehicle and frames from its own sentinel.
 */
fun CommandTable?.frame(command: Command): IntArray? {
    if (command.module != Signal.MODULE_CANBUS) return command.payload
    val index = this?.get(command) ?: return null
    return intArrayOf(1, index)
}

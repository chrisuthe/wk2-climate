package com.wk2.climate.app

import android.content.Context
import android.content.SharedPreferences
import com.wk2.climate.bus.Question
import com.wk2.climate.bus.SavedVehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The persisted [SavedVehicle]: the last canbus id seen and the setup answers.
 *
 * The service writes the id when the vehicle reports it; [SetupActivity]
 * writes the answers. Both run in this app's one process and each holds its
 * own store, so every store listens for changes and [saved] reflects a write
 * from either side as soon as it lands.
 *
 * `SharedPreferences` keeps the registered listener only weakly, which is why
 * it is a field here rather than a lambda passed inline.
 */
class VehicleStore(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _saved = MutableStateFlow(read())
    val saved: StateFlow<SavedVehicle> = _saved.asStateFlow()

    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> _saved.value = read() }

    init {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun save(value: SavedVehicle) {
        prefs.edit().apply {
            clear()
            value.id?.let { putInt(KEY_ID, it) }
            for ((q, yes) in value.answers) putBoolean(KEY_ANSWER + q.name, yes)
        }.apply()
        _saved.value = value
    }

    private fun read(): SavedVehicle {
        val id = if (prefs.contains(KEY_ID)) prefs.getInt(KEY_ID, 0) else null
        val answers = Question.entries
            .filter { prefs.contains(KEY_ANSWER + it.name) }
            .associateWith { prefs.getBoolean(KEY_ANSWER + it.name, false) }
        return SavedVehicle(id, answers)
    }

    private companion object {
        const val PREFS = "vehicle"
        const val KEY_ID = "id"
        const val KEY_ANSWER = "answer."
    }
}

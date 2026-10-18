package io.github.waltoncomonline.samsungmimh02.schedule

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import io.github.waltoncomonline.samsungmimh02.protocol.AcOperation
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek

private val Context.scheduleDataStore by preferencesDataStore(name = "schedules")

class ScheduleStore(context: Context) {
    private val dataStore = context.applicationContext.scheduleDataStore

    val schedules: Flow<List<AcSchedule>> = dataStore.data.map { preferences ->
        decode(preferences[SCHEDULES].orEmpty())
    }

    suspend fun get(id: String): AcSchedule? = schedules.first().firstOrNull { it.id == id }

    suspend fun upsert(schedule: AcSchedule) {
        dataStore.edit { preferences ->
            val current = decode(preferences[SCHEDULES].orEmpty()).toMutableList()
            val index = current.indexOfFirst { it.id == schedule.id }
            if (index >= 0) current[index] = schedule else current += schedule
            preferences[SCHEDULES] = encode(current)
        }
    }

    suspend fun delete(id: String) {
        dataStore.edit { preferences ->
            preferences[SCHEDULES] = encode(
                decode(preferences[SCHEDULES].orEmpty()).filterNot { it.id == id },
            )
        }
    }

    private fun encode(schedules: List<AcSchedule>): String = JSONArray().apply {
        schedules.forEach { schedule ->
            put(JSONObject().apply {
                put("id", schedule.id)
                put("name", schedule.name)
                put("hour", schedule.hour)
                put("minute", schedule.minute)
                put("days", JSONArray(schedule.days.map(DayOfWeek::getValue)))
                put("enabled", schedule.enabled)
                put("powerOn", schedule.powerOn)
                put("operation", schedule.operation.name)
                put("targetTemperatureC", schedule.targetTemperatureC)
            })
        }
    }.toString()

    private fun decode(value: String): List<AcSchedule> {
        if (value.isBlank()) return emptyList()
        val array = runCatching { JSONArray(value) }.getOrNull() ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                runCatching {
                    val daysJson = item.getJSONArray("days")
                    val days = buildSet {
                        for (dayIndex in 0 until daysJson.length()) {
                            add(DayOfWeek.of(daysJson.getInt(dayIndex)))
                        }
                    }
                    AcSchedule(
                        id = item.getString("id"),
                        name = item.getString("name"),
                        hour = item.getInt("hour"),
                        minute = item.getInt("minute"),
                        days = days,
                        enabled = item.optBoolean("enabled", true),
                        powerOn = item.optBoolean("powerOn", true),
                        operation = AcOperation.valueOf(item.optString("operation", AcOperation.Auto.name)),
                        targetTemperatureC = item.optInt("targetTemperatureC", 24),
                    )
                }.getOrNull()?.let(::add)
            }
        }
    }

    companion object {
        private val SCHEDULES = stringPreferencesKey("schedules_json")
    }
}

package io.github.waltoncomonline.samsungmimh02.schedule

import io.github.waltoncomonline.samsungmimh02.protocol.AcCommand
import io.github.waltoncomonline.samsungmimh02.protocol.AcOperation
import java.time.DayOfWeek
import java.util.UUID

data class AcSchedule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val hour: Int,
    val minute: Int,
    val days: Set<DayOfWeek>,
    val enabled: Boolean = true,
    val powerOn: Boolean,
    val operation: AcOperation = AcOperation.Auto,
    val targetTemperatureC: Int = 24,
) {
    init {
        require(name.isNotBlank()) { "Schedule name is required" }
        require(hour in 0..23) { "Hour is out of range" }
        require(minute in 0..59) { "Minute is out of range" }
        require(days.isNotEmpty()) { "At least one day is required" }
        require(targetTemperatureC in AcCommand.MIN_TARGET_C..AcCommand.MAX_TARGET_C) {
            "Target temperature is out of range"
        }
    }

    fun commands(): List<AcCommand> = if (powerOn) {
        listOf(
            AcCommand.Power(true),
            AcCommand.Operation(operation),
            AcCommand.TargetTemperature(targetTemperatureC),
        )
    } else {
        listOf(AcCommand.Power(false))
    }
}

package io.github.waltoncomonline.samsungmimh02.schedule

import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime

object SchedulePlanner {
    fun nextRun(schedule: AcSchedule, now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        require(schedule.enabled) { "Disabled schedules have no next run" }
        val time = LocalTime.of(schedule.hour, schedule.minute)
        for (daysAhead in 0..7) {
            val date = now.toLocalDate().plusDays(daysAhead.toLong())
            if (date.dayOfWeek !in schedule.days) continue
            val candidate = ZonedDateTime.of(date, time, now.zone)
            if (candidate.isAfter(now)) return candidate
        }
        error("Could not calculate the next schedule occurrence")
    }

    fun delayMillis(schedule: AcSchedule, now: ZonedDateTime = ZonedDateTime.now()): Long =
        Duration.between(now, nextRun(schedule, now)).toMillis().coerceAtLeast(0L)
}

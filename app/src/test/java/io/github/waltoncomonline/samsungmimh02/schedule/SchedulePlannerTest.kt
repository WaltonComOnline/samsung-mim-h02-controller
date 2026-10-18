package io.github.waltoncomonline.samsungmimh02.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime

class SchedulePlannerTest {
    private val zone = ZoneId.of("Australia/Sydney")

    @Test
    fun `uses today when scheduled time is still ahead`() {
        val now = ZonedDateTime.of(2026, 9, 14, 8, 0, 0, 0, zone)
        val schedule = schedule(hour = 9, days = setOf(DayOfWeek.MONDAY))

        assertEquals(
            ZonedDateTime.of(2026, 9, 14, 9, 0, 0, 0, zone),
            SchedulePlanner.nextRun(schedule, now),
        )
    }

    @Test
    fun `moves to next selected week when today's time passed`() {
        val now = ZonedDateTime.of(2026, 9, 14, 10, 0, 0, 0, zone)
        val schedule = schedule(hour = 9, days = setOf(DayOfWeek.MONDAY))

        assertEquals(
            ZonedDateTime.of(2026, 9, 21, 9, 0, 0, 0, zone),
            SchedulePlanner.nextRun(schedule, now),
        )
    }

    @Test
    fun `chooses earliest upcoming selected day`() {
        val now = ZonedDateTime.of(2026, 9, 14, 10, 0, 0, 0, zone)
        val schedule = schedule(
            hour = 7,
            days = setOf(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
        )

        assertEquals(DayOfWeek.WEDNESDAY, SchedulePlanner.nextRun(schedule, now).dayOfWeek)
    }

    private fun schedule(hour: Int, days: Set<DayOfWeek>) = AcSchedule(
        name = "Test",
        hour = hour,
        minute = 0,
        days = days,
        powerOn = true,
    )
}
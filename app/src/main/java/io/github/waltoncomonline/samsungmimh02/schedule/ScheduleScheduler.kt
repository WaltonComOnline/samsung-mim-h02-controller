package io.github.waltoncomonline.samsungmimh02.schedule

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

object ScheduleScheduler {
    fun replace(context: Context, schedule: AcSchedule, now: ZonedDateTime = ZonedDateTime.now()) {
        val workManager = WorkManager.getInstance(context)
        if (!schedule.enabled) {
            workManager.cancelUniqueWork(workName(schedule.id))
            return
        }
        workManager.enqueueUniqueWork(
            workName(schedule.id),
            ExistingWorkPolicy.REPLACE,
            request(schedule, now),
        )
    }

    fun appendNext(context: Context, schedule: AcSchedule, now: ZonedDateTime = ZonedDateTime.now()) {
        if (!schedule.enabled) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            workName(schedule.id),
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request(schedule, now),
        )
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelUniqueWork(workName(id))
    }

    private fun request(schedule: AcSchedule, now: ZonedDateTime) =
        OneTimeWorkRequestBuilder<ScheduleWorker>()
            .setInputData(Data.Builder().putString(ScheduleWorker.SCHEDULE_ID, schedule.id).build())
            .setInitialDelay(SchedulePlanner.delayMillis(schedule, now), TimeUnit.MILLISECONDS)
            .addTag(workName(schedule.id))
            .build()

    private fun workName(id: String) = "samsung-mim-h02-schedule-$id"
}

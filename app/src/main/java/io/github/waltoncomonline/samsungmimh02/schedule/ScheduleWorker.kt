package io.github.waltoncomonline.samsungmimh02.schedule

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import io.github.waltoncomonline.samsungmimh02.data.TokenStore
import io.github.waltoncomonline.samsungmimh02.protocol.DrcAuthException
import io.github.waltoncomonline.samsungmimh02.protocol.DrcClient

class ScheduleWorker(
    appContext: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(appContext, workerParameters) {
    override suspend fun doWork(): Result {
        val scheduleId = inputData.getString(SCHEDULE_ID) ?: return Result.failure()
        val schedule = ScheduleStore(applicationContext).get(scheduleId) ?: return Result.success()
        if (!schedule.enabled) return Result.success()
        val pairing = TokenStore(applicationContext).load() ?: return Result.failure()

        return try {
            DrcClient(applicationContext).control(
                host = pairing.metadata.host,
                token = pairing.token,
                duid = pairing.metadata.duid,
                commands = schedule.commands(),
            )
            ScheduleScheduler.appendNext(applicationContext, schedule)
            Result.success()
        } catch (_: DrcAuthException) {
            Result.failure()
        } catch (_: Exception) {
            if (runAttemptCount < MAX_RETRIES) Result.retry() else Result.failure()
        }
    }

    companion object {
        const val SCHEDULE_ID = "schedule_id"
        private const val MAX_RETRIES = 3
    }
}

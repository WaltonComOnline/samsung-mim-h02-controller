package io.github.waltoncomonline.samsungmimh02.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ScheduleRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                ScheduleStore(context).schedules.first().forEach { schedule ->
                    ScheduleScheduler.replace(context, schedule)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

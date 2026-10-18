package io.github.waltoncomonline.samsungmimh02.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import io.github.waltoncomonline.samsungmimh02.data.TokenStore
import io.github.waltoncomonline.samsungmimh02.protocol.DrcClient
import io.github.waltoncomonline.samsungmimh02.schedule.AcSchedule
import io.github.waltoncomonline.samsungmimh02.schedule.ScheduleScheduler
import io.github.waltoncomonline.samsungmimh02.schedule.ScheduleStore

data class ScheduleUiState(
    val runningId: String? = null,
    val message: String? = null,
)

class ScheduleViewModel(application: Application) : AndroidViewModel(application) {
    private val store = ScheduleStore(application)
    val schedules: StateFlow<List<AcSchedule>> = store.schedules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )
    private val mutableState = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = mutableState.asStateFlow()

    fun save(schedule: AcSchedule) {
        viewModelScope.launch {
            try {
                store.upsert(schedule)
                ScheduleScheduler.replace(getApplication(), schedule)
                mutableState.value = ScheduleUiState(message = "Schedule saved")
            } catch (error: Exception) {
                mutableState.value = ScheduleUiState(
                    message = "Could not save schedule: ${error.message ?: "unknown error"}",
                )
            }
        }
    }

    fun setEnabled(schedule: AcSchedule, enabled: Boolean) {
        save(schedule.copy(enabled = enabled))
    }

    fun delete(schedule: AcSchedule) {
        viewModelScope.launch {
            try {
                store.delete(schedule.id)
                ScheduleScheduler.cancel(getApplication(), schedule.id)
                mutableState.value = ScheduleUiState(message = "Schedule deleted")
            } catch (error: Exception) {
                mutableState.value = ScheduleUiState(
                    message = "Could not delete schedule: ${error.message ?: "unknown error"}",
                )
            }
        }
    }

    fun runNow(schedule: AcSchedule) {
        if (mutableState.value.runningId != null) return
        viewModelScope.launch {
            mutableState.value = ScheduleUiState(runningId = schedule.id)
            try {
                val pairing = TokenStore(getApplication()).load()
                    ?: error("Pair the controller before running a schedule")
                DrcClient(getApplication()).control(
                    host = pairing.metadata.host,
                    token = pairing.token,
                    duid = pairing.metadata.duid,
                    commands = schedule.commands(),
                )
                mutableState.value = ScheduleUiState(message = "Schedule applied")
            } catch (error: Exception) {
                mutableState.value = ScheduleUiState(message = error.message ?: "Schedule failed")
            }
        }
    }
}

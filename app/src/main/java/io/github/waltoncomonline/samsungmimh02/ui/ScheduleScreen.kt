package io.github.waltoncomonline.samsungmimh02.ui

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.waltoncomonline.samsungmimh02.protocol.AcCommand
import io.github.waltoncomonline.samsungmimh02.protocol.AcOperation
import io.github.waltoncomonline.samsungmimh02.schedule.AcSchedule
import io.github.waltoncomonline.samsungmimh02.schedule.SchedulePlanner
import io.github.waltoncomonline.samsungmimh02.ui.theme.AppCanvas
import io.github.waltoncomonline.samsungmimh02.ui.theme.AppSurface
import io.github.waltoncomonline.samsungmimh02.ui.theme.ErrorRed
import io.github.waltoncomonline.samsungmimh02.ui.theme.PrimaryGreen
import io.github.waltoncomonline.samsungmimh02.ui.theme.PrimaryText
import io.github.waltoncomonline.samsungmimh02.ui.theme.SecondaryText
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleScreen(viewModel: ScheduleViewModel) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AcSchedule?>(null) }
    var showingNew by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppCanvas)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Schedules", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Phone-hosted recurring control", color = SecondaryText)
            }
            FloatingActionButton(
                onClick = { showingNew = true },
                containerColor = PrimaryGreen,
                contentColor = androidx.compose.ui.graphics.Color.White,
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add schedule")
            }
        }

        state.message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }

        if (schedules.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = SecondaryText, modifier = Modifier.size(40.dp))
                Text("No schedules", fontWeight = FontWeight.SemiBold)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(schedules, key = AcSchedule::id) { schedule ->
                    ScheduleRow(
                        schedule = schedule,
                        running = state.runningId == schedule.id,
                        onEdit = { editing = schedule },
                        onEnabled = { viewModel.setEnabled(schedule, it) },
                        onRun = { viewModel.runNow(schedule) },
                        onDelete = { viewModel.delete(schedule) },
                    )
                }
            }
        }
    }

    if (showingNew || editing != null) {
        ScheduleEditor(
            existing = editing,
            onDismiss = {
                showingNew = false
                editing = null
            },
            onSave = {
                viewModel.save(it)
                showingNew = false
                editing = null
            },
        )
    }
}

@Composable
private fun ScheduleRow(
    schedule: AcSchedule,
    running: Boolean,
    onEdit: () -> Unit,
    onEnabled: (Boolean) -> Unit,
    onRun: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(schedule.name, fontWeight = FontWeight.SemiBold)
                    Text(
                        "%02d:%02d · %s".format(
                            schedule.hour,
                            schedule.minute,
                            schedule.days.sorted().joinToString(" ") { it.name.take(2) },
                        ),
                        color = SecondaryText,
                    )
                }
                Switch(checked = schedule.enabled, onCheckedChange = onEnabled)
            }
            Text(
                if (schedule.powerOn) {
                    "On · ${schedule.operation.label} · ${schedule.targetTemperatureC}°C"
                } else {
                    "Power off"
                },
            )
            if (schedule.enabled) {
                Text(
                    "Next ${SchedulePlanner.nextRun(schedule).format(NEXT_RUN_FORMAT)}",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRun, enabled = !running) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (running) "Running" else "Run now")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete schedule", tint = ErrorRed)
                }
            }
        }
    }
}

@Composable
private fun ScheduleEditor(
    existing: AcSchedule?,
    onDismiss: () -> Unit,
    onSave: (AcSchedule) -> Unit,
) {
    val context = LocalContext.current
    var name by remember(existing?.id) { mutableStateOf(existing?.name ?: "Comfort") }
    var hour by remember(existing?.id) { mutableStateOf(existing?.hour ?: 18) }
    var minute by remember(existing?.id) { mutableStateOf(existing?.minute ?: 0) }
    var days by remember(existing?.id) {
        mutableStateOf(existing?.days ?: DayOfWeek.entries.toSet())
    }
    var powerOn by remember(existing?.id) { mutableStateOf(existing?.powerOn ?: true) }
    var operation by remember(existing?.id) { mutableStateOf(existing?.operation ?: AcOperation.Auto) }
    var target by remember(existing?.id) { mutableStateOf(existing?.targetTemperatureC ?: 24) }
    val valid = name.isNotBlank() && days.isNotEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppSurface,
        tonalElevation = 0.dp,
        title = { Text(if (existing == null) "New schedule" else "Edit schedule") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = {
                        TimePickerDialog(context, { _, selectedHour, selectedMinute ->
                            hour = selectedHour
                            minute = selectedMinute
                        }, hour, minute, true).show()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Schedule, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("%02d:%02d".format(hour, minute))
                }
                Text("Days", fontWeight = FontWeight.SemiBold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in days,
                            onClick = {
                                days = if (day in days) days - day else days + day
                            },
                            label = { Text(DAY_LABELS.getValue(day)) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryGreen.copy(alpha = 0.14f),
                                selectedLabelColor = PrimaryText,
                            ),
                        )
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                        Text("Power on")
                    }
                    Switch(checked = powerOn, onCheckedChange = { powerOn = it })
                }
                if (powerOn) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        AcOperation.entries.forEach { mode ->
                            FilterChip(
                                selected = mode == operation,
                                onClick = { operation = mode },
                                label = { Text(mode.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PrimaryGreen.copy(alpha = 0.14f),
                                    selectedLabelColor = PrimaryText,
                                ),
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { target-- },
                            enabled = target > AcCommand.MIN_TARGET_C,
                        ) { Text("−") }
                        Text("$target°C", fontWeight = FontWeight.Bold)
                        TextButton(
                            onClick = { target++ },
                            enabled = target < AcCommand.MAX_TARGET_C,
                        ) { Text("+") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        AcSchedule(
                            id = existing?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            hour = hour,
                            minute = minute,
                            days = days,
                            enabled = existing?.enabled ?: true,
                            powerOn = powerOn,
                            operation = operation,
                            targetTemperatureC = target,
                        ),
                    )
                },
                enabled = valid,
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private val NEXT_RUN_FORMAT = DateTimeFormatter.ofPattern("EEE HH:mm")
private val DAY_LABELS = mapOf(
    DayOfWeek.MONDAY to "Mo",
    DayOfWeek.TUESDAY to "Tu",
    DayOfWeek.WEDNESDAY to "We",
    DayOfWeek.THURSDAY to "Th",
    DayOfWeek.FRIDAY to "Fr",
    DayOfWeek.SATURDAY to "Sa",
    DayOfWeek.SUNDAY to "Su",
)

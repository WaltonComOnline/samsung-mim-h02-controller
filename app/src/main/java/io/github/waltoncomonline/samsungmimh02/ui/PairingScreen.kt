package io.github.waltoncomonline.samsungmimh02.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import io.github.waltoncomonline.samsungmimh02.ui.theme.AccentAmber
import io.github.waltoncomonline.samsungmimh02.ui.theme.AppCanvas
import io.github.waltoncomonline.samsungmimh02.ui.theme.ErrorRed
import io.github.waltoncomonline.samsungmimh02.ui.theme.PrimaryGreen
import io.github.waltoncomonline.samsungmimh02.ui.theme.PrimaryText
import io.github.waltoncomonline.samsungmimh02.ui.theme.SecondaryText
import io.github.waltoncomonline.samsungmimh02.protocol.AcCommand
import io.github.waltoncomonline.samsungmimh02.protocol.AcFanSpeed
import io.github.waltoncomonline.samsungmimh02.protocol.AcOperation
import io.github.waltoncomonline.samsungmimh02.protocol.AcState
import java.util.Locale

@Composable
fun PairingScreen(
    viewModel: PairingViewModel,
    onRevealToken: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.isPaired) {
        while (state.isPaired) {
            delay(10_000)
            viewModel.refreshState()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppCanvas),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Samsung MIM-H02",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (state.isPaired) "Samsung MIM-H02 control" else "Samsung MIM-H02 pairing",
                    color = SecondaryText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            StatusBand(state)

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Controller", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = state.host,
                    onValueChange = viewModel::setHost,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy && !state.isPaired,
                    label = { Text("IP address") },
                    leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                    supportingText = { Text("DRC-1.00 controller · TCP 2878") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                if (!state.isPaired) {
                    OutlinedButton(
                        onClick = viewModel::discoverControllers,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (state.discoveredControllers.isEmpty()) "Search local network" else "Search again")
                    }
                    state.discoveredControllers.forEach { controller ->
                        FilterChip(
                            selected = state.host == controller.host,
                            onClick = { viewModel.setHost(controller.host) },
                            enabled = !state.isBusy,
                            label = { Text("Samsung MIM-H02 · ${controller.host}") },
                            leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PrimaryGreen.copy(alpha = 0.14f),
                                selectedLabelColor = PrimaryText,
                            ),
                        )
                    }
                }
            }

            if (state.isPaired) {
                PairedDevice(
                    state = state,
                    onRefresh = viewModel::refreshState,
                    onPowerChanged = viewModel::setPower,
                    onOperationChanged = viewModel::setOperation,
                    onTargetChanged = viewModel::setTargetTemperature,
                    onFanSpeedChanged = viewModel::setFanSpeed,
                    onSwingChanged = viewModel::setSwing,
                    onVerify = viewModel::verifySavedToken,
                    onReveal = onRevealToken,
                    onHide = viewModel::hideToken,
                    onForget = viewModel::forgetPairing,
                )
            } else {
                CaptureActions(
                    state = state,
                    onCapture = viewModel::captureToken,
                    onCancel = viewModel::cancelCapture,
                )
            }

            state.error?.let { error ->
                Surface(
                    color = ErrorRed.copy(alpha = 0.10f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = error,
                        color = ErrorRed,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }

            HorizontalDivider()
            Text(
                text = "Legacy TLS is enabled only for this private-LAN controller address. The captured token is encrypted with Android Keystore.",
                color = SecondaryText,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun StatusBand(state: PairingUiState) {
    val (label, color) = when (state.status) {
        PairingStatus.Ready -> "Ready to pair" to SecondaryText
        PairingStatus.Discovering -> {
            val progress = if (state.discoveryTotal > 0) {
                " · ${state.discoveryCompleted}/${state.discoveryTotal}"
            } else {
                ""
            }
            "Searching local network$progress" to AccentAmber
        }
        PairingStatus.Connecting -> "Connecting securely" to AccentAmber
        PairingStatus.WaitingForButton -> "Press AP / Auth now · ${state.secondsRemaining}s" to AccentAmber
        PairingStatus.Authenticating -> "Validating captured token" to AccentAmber
        PairingStatus.Paired -> when {
            state.pendingCommand != null -> "Applying ${state.pendingCommand.lowercase()}" to AccentAmber
            state.isRefreshing -> "Refreshing AC state" to AccentAmber
            state.acState != null -> "Online and authenticated" to PrimaryGreen
            else -> "Paired and encrypted" to PrimaryGreen
        }
        PairingStatus.Verifying -> "Verifying saved token" to AccentAmber
        PairingStatus.Error -> "Pairing needs attention" to ErrorRed
    }
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (state.isBusy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
            } else {
                Icon(Icons.Default.Key, contentDescription = null, tint = color)
            }
            Text(label, color = color, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun CaptureActions(
    state: PairingUiState,
    onCapture: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = if (state.status == PairingStatus.WaitingForButton) {
                "Waiting for the token from the Wi-Fi module."
            } else {
                "Press AP / Auth on the Wi-Fi module, then start capture within 10 seconds."
            },
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(
            onClick = onCapture,
            enabled = !state.isBusy && state.host.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(Icons.Default.Key, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Capture and authenticate")
        }
        if (state.isBusy) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Cancel, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Cancel")
            }
        }
    }
}

@Composable
private fun PairedDevice(
    state: PairingUiState,
    onRefresh: () -> Unit,
    onPowerChanged: (Boolean) -> Unit,
    onOperationChanged: (AcOperation) -> Unit,
    onTargetChanged: (Int) -> Unit,
    onFanSpeedChanged: (AcFanSpeed) -> Unit,
    onSwingChanged: (Boolean) -> Unit,
    onVerify: () -> Unit,
    onReveal: () -> Unit,
    onHide: () -> Unit,
    onForget: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = PrimaryGreen)
            Column {
                Text(state.model ?: "Samsung AC", fontWeight = FontWeight.SemiBold)
                Text(state.duid.orEmpty(), color = SecondaryText, style = MaterialTheme.typography.bodySmall)
            }
        }
        state.acState?.let { acState ->
            AcControls(
                state = acState,
                enabled = state.pendingCommand == null && !state.isRefreshing,
                onRefresh = onRefresh,
                onPowerChanged = onPowerChanged,
                onOperationChanged = onOperationChanged,
                onTargetChanged = onTargetChanged,
                onFanSpeedChanged = onFanSpeedChanged,
                onSwingChanged = onSwingChanged,
            )
        } ?: OutlinedButton(
            onClick = onRefresh,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Load AC state")
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        Text("Pairing security", fontWeight = FontWeight.SemiBold)
        Button(
            onClick = onVerify,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Verify authentication")
        }
        OutlinedButton(
            onClick = if (state.revealedToken == null) onReveal else onHide,
            enabled = !state.isBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = if (state.revealedToken == null) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = null,
            )
            Spacer(Modifier.size(8.dp))
            Text(if (state.revealedToken == null) "Reveal token" else "Hide token")
        }
        state.revealedToken?.let { token ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                SelectionContainer {
                    Text(
                        text = token,
                        modifier = Modifier.padding(14.dp),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        OutlinedButton(onClick = onForget, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = ErrorRed)
            Spacer(Modifier.size(8.dp))
            Text("Forget pairing", color = ErrorRed)
        }
    }
}

@Composable
private fun AcControls(
    state: AcState,
    enabled: Boolean,
    onRefresh: () -> Unit,
    onPowerChanged: (Boolean) -> Unit,
    onOperationChanged: (AcOperation) -> Unit,
    onTargetChanged: (Int) -> Unit,
    onFanSpeedChanged: (AcFanSpeed) -> Unit,
    onSwingChanged: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Room", color = SecondaryText, style = MaterialTheme.typography.labelLarge)
                    Text(
                        text = state.currentTemperatureC?.let {
                            String.format(Locale.getDefault(), "%.1f°C", it)
                        } ?: "--",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Icon(Icons.Default.Thermostat, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PowerSettingsNew, contentDescription = null)
                Text("Power", fontWeight = FontWeight.SemiBold)
            }
            Switch(
                checked = state.powerOn == true,
                onCheckedChange = onPowerChanged,
                enabled = enabled && state.powerOn != null,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Target temperature", fontWeight = FontWeight.SemiBold)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = { state.targetTemperatureC?.minus(1)?.let(onTargetChanged) },
                    enabled = enabled && (state.targetTemperatureC ?: 0) > AcCommand.MIN_TARGET_C,
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease target temperature")
                }
                Text(
                    text = state.targetTemperatureC?.let { "$it°C" } ?: "--",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                IconButton(
                    onClick = { state.targetTemperatureC?.plus(1)?.let(onTargetChanged) },
                    enabled = enabled && (state.targetTemperatureC ?: 99) < AcCommand.MAX_TARGET_C,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase target temperature")
                }
            }
        }

        ChoiceRow(
            title = "Mode",
            icon = { Icon(Icons.Default.AcUnit, contentDescription = null) },
            choices = AcOperation.entries,
            selected = state.operation,
            enabled = enabled,
            label = AcOperation::label,
            onSelected = onOperationChanged,
        )

        ChoiceRow(
            title = "Fan speed",
            choices = AcFanSpeed.entries,
            selected = state.fanSpeed,
            enabled = enabled,
            label = AcFanSpeed::label,
            onSelected = onFanSpeedChanged,
        )

        state.swingOn?.let { swingOn ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Swing", fontWeight = FontWeight.SemiBold)
                Switch(checked = swingOn, onCheckedChange = onSwingChanged, enabled = enabled)
            }
        }

        OutlinedButton(onClick = onRefresh, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Refresh, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Refresh state")
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    title: String,
    choices: List<T>,
    selected: T?,
    enabled: Boolean,
    label: (T) -> String,
    onSelected: (T) -> Unit,
    icon: (@Composable () -> Unit)? = null,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            icon?.invoke()
            Text(title, fontWeight = FontWeight.SemiBold)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            choices.forEach { choice ->
                FilterChip(
                    selected = choice == selected,
                    onClick = { onSelected(choice) },
                    enabled = enabled,
                    label = { Text(label(choice)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PrimaryGreen.copy(alpha = 0.14f),
                        selectedLabelColor = PrimaryText,
                    ),
                )
            }
        }
    }
}

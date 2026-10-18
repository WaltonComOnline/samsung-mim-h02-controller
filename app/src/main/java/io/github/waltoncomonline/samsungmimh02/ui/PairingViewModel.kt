package io.github.waltoncomonline.samsungmimh02.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.waltoncomonline.samsungmimh02.data.PairingMetadata
import io.github.waltoncomonline.samsungmimh02.data.PendingTokenImport
import io.github.waltoncomonline.samsungmimh02.data.TokenStore
import io.github.waltoncomonline.samsungmimh02.protocol.DrcAuthException
import io.github.waltoncomonline.samsungmimh02.protocol.DrcClient
import io.github.waltoncomonline.samsungmimh02.protocol.DrcException
import io.github.waltoncomonline.samsungmimh02.protocol.AcCommand
import io.github.waltoncomonline.samsungmimh02.protocol.AcFanSpeed
import io.github.waltoncomonline.samsungmimh02.protocol.AcOperation
import io.github.waltoncomonline.samsungmimh02.protocol.AcState
import io.github.waltoncomonline.samsungmimh02.protocol.ControllerDiscovery
import io.github.waltoncomonline.samsungmimh02.protocol.DiscoveredController
import org.json.JSONObject

enum class PairingStatus {
    Ready,
    Discovering,
    Connecting,
    WaitingForButton,
    Authenticating,
    Paired,
    Verifying,
    Error,
}

data class PairingUiState(
    val host: String = "",
    val status: PairingStatus = PairingStatus.Ready,
    val secondsRemaining: Int = 40,
    val duid: String? = null,
    val model: String? = null,
    val error: String? = null,
    val revealedToken: String? = null,
    val acState: AcState? = null,
    val isRefreshing: Boolean = false,
    val pendingCommand: String? = null,
    val discoveredControllers: List<DiscoveredController> = emptyList(),
    val discoveryCompleted: Int = 0,
    val discoveryTotal: Int = 0,
) {
    val isBusy: Boolean
        get() = status in setOf(
            PairingStatus.Discovering,
            PairingStatus.Connecting,
            PairingStatus.WaitingForButton,
            PairingStatus.Authenticating,
            PairingStatus.Verifying,
        )
    val isPaired: Boolean get() = duid != null
}

class PairingViewModel(application: Application) : AndroidViewModel(application) {
    private val tokenStore = TokenStore(application)
    private val client = DrcClient(application)
    private val discovery = ControllerDiscovery(application)
    private val mutableState = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = mutableState.asStateFlow()

    private var operation: Job? = null
    private var countdown: Job? = null
    private var revealTimeout: Job? = null

    init {
        viewModelScope.launch {
            val saved = tokenStore.metadata()
            saved?.let {
                mutableState.update {
                    it.copy(
                        host = saved.host,
                        status = PairingStatus.Paired,
                        duid = saved.duid,
                        model = saved.model,
                    )
                }
                loadState()
            }
            if (saved == null) {
                val imported = importPendingTokenFile()
                if (!imported) discoverControllers()
            }
        }
    }

    fun setHost(host: String) {
        if (mutableState.value.isBusy) return
        mutableState.update { it.copy(host = host.trim(), error = null) }
    }

    fun captureToken() {
        if (operation?.isActive == true) return
        val host = mutableState.value.host
        if (host.isBlank()) {
            mutableState.update { it.copy(status = PairingStatus.Error, error = "Enter the controller IP address") }
            return
        }
        operation = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    status = PairingStatus.Connecting,
                    secondsRemaining = CAPTURE_SECONDS,
                    error = null,
                    revealedToken = null,
                )
            }
            try {
                val result = client.captureAndAuthenticate(
                    host = host,
                    timeoutSeconds = CAPTURE_SECONDS,
                    onWaitingForButton = {
                        mutableState.update { it.copy(status = PairingStatus.WaitingForButton) }
                        startCountdown()
                    },
                )
                mutableState.update { it.copy(status = PairingStatus.Authenticating) }
                val metadata = PairingMetadata(
                    host = host,
                    port = 2878,
                    duid = result.device.duid,
                    model = result.device.model,
                )
                tokenStore.save(metadata, result.token)
                val acState = client.readState(host, result.token, metadata.duid)
                mutableState.update {
                    it.copy(
                        status = PairingStatus.Paired,
                        duid = metadata.duid,
                        model = metadata.model,
                        error = null,
                        acState = acState,
                    )
                }
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(status = PairingStatus.Error, error = userMessage(error))
                }
            } finally {
                countdown?.cancel()
            }
        }
    }

    fun discoverControllers() {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            mutableState.update {
                it.copy(
                    status = PairingStatus.Discovering,
                    discoveredControllers = emptyList(),
                    discoveryCompleted = 0,
                    discoveryTotal = 0,
                    error = null,
                )
            }
            try {
                val controllers = discovery.discover { completed, total ->
                    mutableState.update {
                        it.copy(discoveryCompleted = completed, discoveryTotal = total)
                    }
                }
                mutableState.update {
                    it.copy(
                        status = PairingStatus.Ready,
                        host = controllers.singleOrNull()?.host ?: it.host,
                        discoveredControllers = controllers,
                        error = if (controllers.isEmpty()) {
                            "No MIM-H02 controller was found. Check Wi-Fi or enter its IP address."
                        } else {
                            null
                        },
                    )
                }
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(status = PairingStatus.Ready, error = userMessage(error))
                }
            }
        }
    }

    fun importTokenFile(contents: String) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            mutableState.update {
                it.copy(status = PairingStatus.Authenticating, error = null, revealedToken = null)
            }
            try {
                val (host, token) = extractPairingImport(contents)
                mutableState.update { it.copy(host = host) }
                val device = client.authenticate(host, token)
                val metadata = PairingMetadata(
                    host = host,
                    port = 2878,
                    duid = device.duid,
                    model = device.model,
                )
                tokenStore.save(metadata, token)
                val acState = client.readState(host, token, metadata.duid)
                mutableState.update {
                    it.copy(
                        status = PairingStatus.Paired,
                        duid = metadata.duid,
                        model = metadata.model,
                        error = null,
                        acState = acState,
                    )
                }
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(status = PairingStatus.Error, error = userMessage(error))
                }
            }
        }
    }

    fun verifySavedToken() {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            mutableState.update { it.copy(status = PairingStatus.Verifying, error = null) }
            try {
                val saved = tokenStore.load() ?: throw DrcAuthException("No saved token")
                val device = client.authenticate(saved.metadata.host, saved.token)
                val acState = client.readState(saved.metadata.host, saved.token, device.duid)
                mutableState.update {
                    it.copy(
                        status = PairingStatus.Paired,
                        duid = device.duid,
                        model = device.model,
                        error = null,
                        acState = acState,
                    )
                }
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(status = PairingStatus.Error, error = userMessage(error))
                }
            }
        }
    }

    fun cancelCapture() {
        operation?.cancel()
        countdown?.cancel()
        mutableState.update { it.copy(status = PairingStatus.Ready, error = null) }
    }

    fun refreshState() {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            loadState(showRefreshing = true)
        }
    }

    fun setPower(on: Boolean) = sendCommand(AcCommand.Power(on), "Power")

    fun setOperation(operation: AcOperation) =
        sendCommand(AcCommand.Operation(operation), "Mode")

    fun setTargetTemperature(celsius: Int) =
        sendCommand(AcCommand.TargetTemperature(celsius), "Temperature")

    fun setFanSpeed(speed: AcFanSpeed) =
        sendCommand(AcCommand.FanSpeed(speed), "Fan speed")

    fun setSwing(on: Boolean) = sendCommand(AcCommand.Swing(on), "Swing")

    fun revealToken() {
        revealTimeout?.cancel()
        revealTimeout = viewModelScope.launch {
            val token = tokenStore.load()?.token ?: return@launch
            mutableState.update { it.copy(revealedToken = token) }
            delay(TOKEN_REVEAL_MS)
            mutableState.update { it.copy(revealedToken = null) }
        }
    }

    fun hideToken() {
        revealTimeout?.cancel()
        mutableState.update { it.copy(revealedToken = null) }
    }

    fun forgetPairing() {
        if (operation?.isActive == true) return
        viewModelScope.launch {
            tokenStore.clear()
            mutableState.value = PairingUiState()
            discoverControllers()
        }
    }

    private fun startCountdown() {
        countdown?.cancel()
        countdown = viewModelScope.launch {
            for (remaining in CAPTURE_SECONDS downTo 0) {
                mutableState.update { it.copy(secondsRemaining = remaining) }
                delay(1_000)
            }
        }
    }

    private suspend fun importPendingTokenFile(): Boolean {
        val contents = withContext(Dispatchers.IO) {
            PendingTokenImport.consume(getApplication())
        }
        if (contents != null) {
            importTokenFile(contents)
        }
        return contents != null
    }

    private suspend fun loadState(showRefreshing: Boolean = false) {
        if (showRefreshing) {
            mutableState.update { it.copy(isRefreshing = true, error = null) }
        }
        try {
            val saved = tokenStore.load() ?: throw DrcAuthException("No saved token")
            val acState = client.readState(
                host = saved.metadata.host,
                token = saved.token,
                duid = saved.metadata.duid,
            )
            mutableState.update {
                it.copy(
                    status = PairingStatus.Paired,
                    acState = acState,
                    isRefreshing = false,
                    error = null,
                )
            }
        } catch (error: Exception) {
            mutableState.update {
                it.copy(
                    status = PairingStatus.Error,
                    isRefreshing = false,
                    error = userMessage(error),
                )
            }
        }
    }

    private fun sendCommand(command: AcCommand, label: String) {
        if (operation?.isActive == true) return
        operation = viewModelScope.launch {
            mutableState.update { it.copy(pendingCommand = label, error = null) }
            try {
                val saved = tokenStore.load() ?: throw DrcAuthException("No saved token")
                val acState = client.control(
                    host = saved.metadata.host,
                    token = saved.token,
                    duid = saved.metadata.duid,
                    command = command,
                )
                mutableState.update {
                    it.copy(
                        status = PairingStatus.Paired,
                        acState = acState,
                        pendingCommand = null,
                        error = null,
                    )
                }
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        status = PairingStatus.Error,
                        pendingCommand = null,
                        error = userMessage(error),
                    )
                }
            }
        }
    }

    private fun userMessage(error: Exception): String = when (error) {
        is DrcAuthException -> "Authentication failed. Capture a new token and try again."
        is DrcException -> error.message ?: "The controller connection failed."
        else -> "Pairing failed: ${error.message ?: error.javaClass.simpleName}"
    }

    private fun extractPairingImport(contents: String): Pair<String, String> {
        val trimmed = contents.trim()
        val json = if (PROTOCOL_VALUE.matches(trimmed)) null else JSONObject(trimmed)
        val token = if (json == null) {
            trimmed
        } else {
            json.optString("token")
        }
        require(PROTOCOL_VALUE.matches(token)) { "The selected file has no valid device token" }
        val host = json?.optString("host")?.takeIf(String::isNotBlank) ?: mutableState.value.host
        require(HOST.matches(host)) { "Select or enter the controller IP address first" }
        return host to token
    }

    companion object {
        private val PROTOCOL_VALUE = Regex("[A-Za-z0-9._:-]{1,256}")
        private val HOST = Regex("(?:\\d{1,3}\\.){3}\\d{1,3}")
        private const val CAPTURE_SECONDS = 40
        private const val TOKEN_REVEAL_MS = 30_000L
    }
}

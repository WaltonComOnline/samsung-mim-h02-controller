package io.github.waltoncomonline.samsungmimh02.protocol

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class PairingResult(
    val token: String,
    val device: DrcDevice,
)

class DrcClient(
    context: Context,
) {
    private val socketFactory = LegacyTlsSocketFactory(context)

    suspend fun isController(host: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            socketFactory.open(
                host = host,
                port = LegacyTlsSocketFactory.CONTROLLER_PORT,
                connectTimeoutMillis = DISCOVERY_CONNECT_TIMEOUT_MS,
                readTimeoutMillis = DISCOVERY_RESPONSE_TIMEOUT_MS,
                logFailure = false,
            ).use { io ->
                DrcProtocol(io).readGreeting(
                    responseTimeoutMillis = DISCOVERY_RESPONSE_TIMEOUT_MS,
                    invalidateTimeoutMillis = DISCOVERY_INVALIDATE_TIMEOUT_MS,
                )
            }
            true
        }.getOrDefault(false)
    }

    suspend fun captureAndAuthenticate(
        host: String,
        timeoutSeconds: Int = 40,
        onWaitingForButton: () -> Unit = {},
    ): PairingResult = withContext(Dispatchers.IO) {
        val token = socketFactory.open(host, LegacyTlsSocketFactory.CONTROLLER_PORT).use { io ->
            DrcProtocol(io).run {
                readGreeting()
                captureToken(timeoutSeconds, onWaitingForButton)
            }
        }
        val device = authenticate(host, token)
        PairingResult(token, device)
    }

    suspend fun authenticate(host: String, token: String): DrcDevice = withContext(Dispatchers.IO) {
        socketFactory.open(host, LegacyTlsSocketFactory.CONTROLLER_PORT).use { io ->
            DrcProtocol(io).run {
                readGreeting()
                authenticate(token)
                discoverFirstDevice()
            }
        }
    }

    suspend fun readState(host: String, token: String, duid: String): AcState = withContext(Dispatchers.IO) {
        socketFactory.open(host, LegacyTlsSocketFactory.CONTROLLER_PORT).use { io ->
            DrcProtocol(io).run {
                readGreeting()
                authenticate(token)
                readState(duid)
            }
        }
    }

    suspend fun control(
        host: String,
        token: String,
        duid: String,
        command: AcCommand,
    ): AcState = control(host, token, duid, listOf(command))

    suspend fun control(
        host: String,
        token: String,
        duid: String,
        commands: List<AcCommand>,
    ): AcState = withContext(Dispatchers.IO) {
        require(commands.isNotEmpty()) { "At least one command is required" }
        socketFactory.open(host, LegacyTlsSocketFactory.CONTROLLER_PORT).use { io ->
            DrcProtocol(io).run {
                readGreeting()
                authenticate(token)
                commands.forEach { control(duid, it) }
                readState(duid)
            }
        }
    }

    companion object {
        private const val DISCOVERY_CONNECT_TIMEOUT_MS = 400
        private const val DISCOVERY_RESPONSE_TIMEOUT_MS = 2_500
        private const val DISCOVERY_INVALIDATE_TIMEOUT_MS = 250
    }
}

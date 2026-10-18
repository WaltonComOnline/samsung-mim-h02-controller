package io.github.waltoncomonline.samsungmimh02.protocol

import android.util.Log
import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.StringReader
import java.net.SocketTimeoutException
import javax.xml.parsers.DocumentBuilderFactory

internal interface DrcIo : Closeable {
    fun readLine(timeoutMillis: Int): String?
    fun writeLine(value: String)
}

internal class SocketDrcIo(
    private val socket: java.net.Socket,
) : DrcIo {
    private val reader = BufferedReader(socket.getInputStream().reader(Charsets.UTF_8))
    private val writer = BufferedWriter(socket.getOutputStream().writer(Charsets.UTF_8))

    override fun readLine(timeoutMillis: Int): String? {
        socket.soTimeout = timeoutMillis
        return try {
            reader.readLine()
        } catch (error: SocketTimeoutException) {
            throw DrcTimeoutException("The controller did not respond in time", error)
        }
    }

    override fun writeLine(value: String) {
        writer.write(value)
        writer.write("\r\n")
        writer.flush()
    }

    override fun close() {
        socket.close()
    }
}

data class DrcDevice(
    val duid: String,
    val model: String,
)

open class DrcException(message: String, cause: Throwable? = null) : Exception(message, cause)
class DrcAuthException(message: String) : DrcException(message)
class DrcTimeoutException(message: String, cause: Throwable? = null) : DrcException(message, cause)

internal class DrcProtocol(
    private val io: DrcIo,
) {
    fun readGreeting(
        responseTimeoutMillis: Int = RESPONSE_TIMEOUT_MS,
        invalidateTimeoutMillis: Int = INVALIDATE_TIMEOUT_MS,
    ) {
        val greeting = io.readLine(responseTimeoutMillis)
            ?: throw DrcException("The controller closed the connection")
        if (!greeting.contains("DRC-1.00")) {
            throw DrcException("Unexpected controller greeting")
        }

        try {
            io.readLine(invalidateTimeoutMillis)
        } catch (_: DrcTimeoutException) {
            // Some firmware omits the optional InvalidateAccount update.
        }
    }

    fun captureToken(timeoutSeconds: Int, onReady: () -> Unit): String {
        io.writeLine("<Request Type=\"GetToken\" />")
        val first = readUntil(RESPONSE_TIMEOUT_MS) { it.type == "GetToken" }
        first.token?.takeIf(String::isNotBlank)?.let { return it }

        onReady()
        val completed = readUntil(timeoutSeconds * 1_000) {
            it.type == "GetToken" && !it.token.isNullOrBlank()
        }
        return completed.token ?: throw DrcException("The controller returned an empty token")
    }

    fun authenticate(token: String) {
        requireProtocolValue(token, "token")
        io.writeLine("<Request Type=\"AuthToken\"><User Token=\"$token\"/></Request>")
        val response = readUntil(RESPONSE_TIMEOUT_MS) { it.type == "AuthToken" }
        if (response.status != "Okay") {
            throw DrcAuthException("The controller rejected the token")
        }
    }

    fun discoverFirstDevice(): DrcDevice {
        io.writeLine("<Request Type=\"DeviceList\" />")
        val response = readUntil(RESPONSE_TIMEOUT_MS) { it.type == "DeviceList" }
        val device = response.element.getElementsByTagName("Device").item(0) as? Element
            ?: throw DrcException("No indoor unit was returned by the controller")
        val duid = device.getAttribute("DUID")
        if (duid.isBlank()) {
            throw DrcException("The controller returned an empty device identifier")
        }
        requireProtocolValue(duid, "device identifier")
        return DrcDevice(
            duid = duid,
            model = device.getAttribute("ModelID").ifBlank { "Samsung AC" },
        )
    }

    fun readState(duid: String): AcState {
        requireProtocolValue(duid, "device identifier")
        io.writeLine("<Request Type=\"DeviceState\" DUID=\"$duid\"></Request>")
        val response = readUntil(RESPONSE_TIMEOUT_MS) { it.type == "DeviceState" }
        val attributes = response.element.getElementsByTagName("Attr")
        val values = buildMap {
            for (index in 0 until attributes.length) {
                val attribute = attributes.item(index) as? Element ?: continue
                put(attribute.getAttribute("ID"), attribute.getAttribute("Value"))
            }
        }
        val currentFahrenheit = values["AC_FUN_TEMPNOW"]?.toDoubleOrNull()
        return AcState(
            powerOn = when (values["AC_FUN_POWER"]) {
                "On" -> true
                "Off" -> false
                else -> null
            },
            operation = AcOperation.fromWire(values["AC_FUN_OPMODE"]),
            currentTemperatureC = currentFahrenheit?.let { (it - 32.0) * 5.0 / 9.0 },
            targetTemperatureC = values["AC_FUN_TEMPSET"]?.toDoubleOrNull()?.toInt(),
            fanSpeed = AcFanSpeed.fromWire(values["AC_FUN_WINDLEVEL"]),
            swingOn = when (values["AC_FUN_FAN"]) {
                "On" -> true
                "Off" -> false
                else -> null
            },
        )
    }

    fun control(duid: String, command: AcCommand) {
        requireProtocolValue(duid, "device identifier")
        io.writeLine(
            "<Request Type=\"DeviceControl\"><Control CommandID=\"${command.attribute}\" " +
                "DUID=\"$duid\"><Attr ID=\"${command.attribute}\" " +
                "Value=\"${command.wireValue}\"/></Control></Request>",
        )
        readUntil(RESPONSE_TIMEOUT_MS) { it.type == "DeviceControl" }
    }

    private fun readUntil(timeoutMillis: Int, predicate: (DrcMessage) -> Boolean): DrcMessage {
        val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
        while (true) {
            val remainingMillis = (deadline - System.nanoTime()) / 1_000_000L
            if (remainingMillis <= 0) {
                throw DrcTimeoutException("The controller did not send the expected response")
            }
            val line = io.readLine(remainingMillis.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                ?: throw DrcException("The controller closed the connection")
            if (!line.trimStart().startsWith("<")) continue

            val message = parseMessage(line)
            if (message.status == "Fail") {
                if (message.type == "AuthToken") {
                    throw DrcAuthException("The controller rejected the token")
                }
                throw DrcException("The controller rejected ${message.type.ifBlank { "the request" }}")
            }
            if (predicate(message)) return message
        }
    }

    companion object {
        private const val RESPONSE_TIMEOUT_MS = 6_000
        private const val INVALIDATE_TIMEOUT_MS = 1_500
        private val protocolValue = Regex("[A-Za-z0-9._:-]{1,256}")

        private fun requireProtocolValue(value: String, label: String) {
            require(protocolValue.matches(value)) { "Invalid $label" }
        }

        private fun parseMessage(xml: String): DrcMessage {
            try {
                if (xml.contains("<!DOCTYPE", ignoreCase = true) ||
                    xml.contains("<!ENTITY", ignoreCase = true)
                ) {
                    throw DrcException("The controller sent forbidden XML declarations")
                }
                val factory = DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = false
                    isExpandEntityReferences = false
                }
                val element = factory.newDocumentBuilder()
                    .parse(InputSource(StringReader(xml)))
                    .documentElement
                return DrcMessage(
                    type = element.getAttribute("Type"),
                    status = element.getAttribute("Status"),
                    token = element.getAttribute("Token").ifBlank { null },
                    element = element,
                )
            } catch (error: Exception) {
                Log.w(TAG, "XML parse failed: ${error.javaClass.simpleName}: ${error.message}")
                throw DrcException("The controller sent malformed XML", error)
            }
        }

        private const val TAG = "SamsungMimH02Drc"
    }
}

private data class DrcMessage(
    val type: String,
    val status: String,
    val token: String?,
    val element: Element,
)

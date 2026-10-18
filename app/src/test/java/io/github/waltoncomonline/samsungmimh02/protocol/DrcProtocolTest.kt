package io.github.waltoncomonline.samsungmimh02.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.ArrayDeque

class DrcProtocolTest {
    @Test
    fun `captures token after ready response`() {
        val io = FakeDrcIo(
            "<Response Type=\"GetToken\" Status=\"Ready\"/>",
            "<Update Type=\"GetToken\" Status=\"Completed\" Token=\"ABC-123\"/>",
        )
        var ready = false

        val token = DrcProtocol(io).captureToken(40) { ready = true }

        assertEquals("ABC-123", token)
        assertEquals(true, ready)
        assertEquals(listOf("<Request Type=\"GetToken\" />"), io.writes)
    }

    @Test
    fun `authenticates and discovers first device`() {
        val io = FakeDrcIo(
            "<Response Type=\"AuthToken\" Status=\"Okay\"/>",
            "<Response Type=\"DeviceList\" Status=\"Okay\"><DeviceList>" +
                "<Device DUID=\"AABBCCDDEEFF\" ModelID=\"CAC\"/>" +
                "</DeviceList></Response>",
        )
        val protocol = DrcProtocol(io)

        protocol.authenticate("ABC-123")
        val device = protocol.discoverFirstDevice()

        assertEquals(DrcDevice("AABBCCDDEEFF", "CAC"), device)
        assertEquals(
            listOf(
                "<Request Type=\"AuthToken\"><User Token=\"ABC-123\"/></Request>",
                "<Request Type=\"DeviceList\" />",
            ),
            io.writes,
        )
    }

    @Test
    fun `surfaces authentication rejection`() {
        val io = FakeDrcIo("<Response Type=\"AuthToken\" Status=\"Fail\" ErrorCode=\"301\"/>")

        assertThrows(DrcAuthException::class.java) {
            DrcProtocol(io).authenticate("ABC-123")
        }
    }

    @Test
    fun `requires drc greeting`() {
        val io = FakeDrcIo("HTTP/1.1 200 OK")

        assertThrows(DrcException::class.java) {
            DrcProtocol(io).readGreeting()
        }
    }

    @Test
    fun `reads typed state and converts room temperature from fahrenheit`() {
        val io = FakeDrcIo(
            "<Response Type=\"DeviceState\" Status=\"Okay\"><DeviceState>" +
                "<Device DUID=\"AABBCCDDEEFF\">" +
                "<Attr ID=\"AC_FUN_POWER\" Value=\"On\"/>" +
                "<Attr ID=\"AC_FUN_OPMODE\" Value=\"Cool\"/>" +
                "<Attr ID=\"AC_FUN_TEMPNOW\" Value=\"76\"/>" +
                "<Attr ID=\"AC_FUN_TEMPSET\" Value=\"23\"/>" +
                "<Attr ID=\"AC_FUN_WINDLEVEL\" Value=\"Mid\"/>" +
                "<Attr ID=\"AC_FUN_FAN\" Value=\"Off\"/>" +
                "</Device></DeviceState></Response>",
        )

        val state = DrcProtocol(io).readState("AABBCCDDEEFF")

        assertEquals(true, state.powerOn)
        assertEquals(AcOperation.Cool, state.operation)
        assertEquals(24.4, state.currentTemperatureC!!, 0.1)
        assertEquals(23, state.targetTemperatureC)
        assertEquals(AcFanSpeed.Mid, state.fanSpeed)
        assertFalse(state.swingOn!!)
        assertEquals(
            listOf("<Request Type=\"DeviceState\" DUID=\"AABBCCDDEEFF\"></Request>"),
            io.writes,
        )
    }

    @Test
    fun `writes one control attribute and waits for acknowledgement`() {
        val io = FakeDrcIo("<Response Type=\"DeviceControl\" Status=\"Okay\"/>")

        DrcProtocol(io).control("AABBCCDDEEFF", AcCommand.TargetTemperature(22))

        assertEquals(
            listOf(
                "<Request Type=\"DeviceControl\"><Control CommandID=\"AC_FUN_TEMPSET\" " +
                    "DUID=\"AABBCCDDEEFF\"><Attr ID=\"AC_FUN_TEMPSET\" " +
                    "Value=\"22\"/></Control></Request>",
            ),
            io.writes,
        )
    }
}

private class FakeDrcIo(vararg responses: String) : DrcIo {
    private val reads = ArrayDeque(responses.toList())
    val writes = mutableListOf<String>()

    override fun readLine(timeoutMillis: Int): String? = reads.pollFirst()
    override fun writeLine(value: String) {
        writes += value
    }
    override fun close() = Unit
}

package io.github.waltoncomonline.samsungmimh02.protocol

import android.content.Context
import android.util.Log
import org.bouncycastle.tls.CertificateRequest
import org.bouncycastle.tls.CipherSuite
import org.bouncycastle.tls.DefaultTlsClient
import org.bouncycastle.tls.ProtocolVersion
import org.bouncycastle.tls.TlsAuthentication
import org.bouncycastle.tls.TlsClientProtocol
import org.bouncycastle.tls.TlsCredentials
import org.bouncycastle.tls.TlsServerCertificate
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto
import java.io.BufferedReader
import java.io.BufferedWriter
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

internal class LegacyTlsSocketFactory(context: Context) {
    init {
        context.applicationContext
    }

    fun open(
        host: String,
        port: Int,
        connectTimeoutMillis: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMillis: Int = connectTimeoutMillis,
        logFailure: Boolean = true,
    ): DrcIo {
        require(port == CONTROLLER_PORT) { "Only the MIM-H02 controller port is allowed" }
        val address = InetAddress.getByName(host)
        require(address.isSiteLocalAddress) { "The controller must use a private LAN address" }

        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(address, port), connectTimeoutMillis)
            socket.soTimeout = readTimeoutMillis
            val protocol = TlsClientProtocol(socket.getInputStream(), socket.getOutputStream())
            protocol.connect(FetchCompatibleTlsClient())
            Log.i(TAG, "Legacy TLS 1.0 connected with Bouncy Castle")
            return BouncyCastleDrcIo(socket, protocol)
        } catch (error: Exception) {
            if (logFailure) {
                Log.w(TAG, "Legacy TLS failed: ${error.javaClass.simpleName}: ${error.message}")
            }
            socket.close()
            throw DrcException("Could not establish legacy TLS with the controller", error)
        }
    }

    private class FetchCompatibleTlsClient : DefaultTlsClient(BcTlsCrypto()) {
        override fun getSupportedVersions(): Array<ProtocolVersion> = arrayOf(ProtocolVersion.TLSv10)

        override fun getSupportedCipherSuites(): IntArray = intArrayOf(
            CipherSuite.TLS_RSA_WITH_AES_256_CBC_SHA,
            CipherSuite.TLS_RSA_WITH_AES_128_CBC_SHA,
        )

        override fun getAuthentication(): TlsAuthentication = object : TlsAuthentication {
            override fun notifyServerCertificate(serverCertificate: TlsServerCertificate) = Unit

            override fun getClientCredentials(certificateRequest: CertificateRequest): TlsCredentials? = null
        }
    }

    private class BouncyCastleDrcIo(
        private val socket: Socket,
        private val protocol: TlsClientProtocol,
    ) : DrcIo {
        private val reader = BufferedReader(protocol.inputStream.reader(Charsets.UTF_8))
        private val writer = BufferedWriter(protocol.outputStream.writer(Charsets.UTF_8))

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
            try {
                protocol.close()
            } finally {
                socket.close()
            }
        }
    }

    companion object {
        private const val TAG = "SamsungMimH02Tls"
        const val CONTROLLER_PORT = 2878
        private const val CONNECT_TIMEOUT_MS = 15_000
    }
}

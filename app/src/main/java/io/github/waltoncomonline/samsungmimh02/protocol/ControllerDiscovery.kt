package io.github.waltoncomonline.samsungmimh02.protocol

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.Inet4Address

data class DiscoveredController(val host: String)

class ControllerDiscovery(
    context: Context,
    private val probe: suspend (String) -> Boolean = DrcClient(context)::isController,
) {
    private val connectivityManager = context.applicationContext
        .getSystemService(ConnectivityManager::class.java)

    suspend fun discover(onProgress: (completed: Int, total: Int) -> Unit): List<DiscoveredController> {
        val (localAddress, prefixLength) = activeLanAddress()
        val candidates = candidateAddresses(localAddress, prefixLength)
        val semaphore = Semaphore(MAX_CONCURRENT_PROBES)
        var completed = 0
        return coroutineScope {
            candidates.map { host ->
                async {
                    semaphore.withPermit {
                        val result = if (probe(host)) DiscoveredController(host) else null
                        synchronized(this@ControllerDiscovery) {
                            completed += 1
                            onProgress(completed, candidates.size)
                        }
                        result
                    }
                }
            }.awaitAll().filterNotNull().sortedBy(DiscoveredController::host)
        }
    }

    @Suppress("DEPRECATION")
    private fun activeLanAddress(): Pair<Inet4Address, Int> {
        val activeNetwork = connectivityManager.activeNetwork
        val network = sequenceOf(activeNetwork)
            .plus(connectivityManager.allNetworks.asSequence())
            .filterNotNull()
            .distinct()
            .firstOrNull(::isLanNetwork)
            ?: throw DrcException("Connect the phone to Wi-Fi before searching")
        val link = connectivityManager.getLinkProperties(network)
            ?.linkAddresses
            ?.firstOrNull { it.address is Inet4Address && !it.address.isLoopbackAddress }
            ?: throw DrcException("The Wi-Fi network has no IPv4 address")
        return link.address as Inet4Address to link.prefixLength
    }

    private fun isLanNetwork(network: android.net.Network): Boolean {
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    companion object {
        private const val MAX_CONCURRENT_PROBES = 32
        private const val WIDEST_SCAN_PREFIX = 24

        internal fun candidateAddresses(localAddress: Inet4Address, prefixLength: Int): List<String> {
            val effectivePrefix = prefixLength.coerceAtLeast(WIDEST_SCAN_PREFIX)
            if (effectivePrefix !in WIDEST_SCAN_PREFIX..30) return emptyList()

            val local = localAddress.address.fold(0) { result, byte ->
                (result shl 8) or (byte.toInt() and 0xff)
            }
            val hostBits = 32 - effectivePrefix
            val mask = -1 shl hostBits
            val network = local and mask
            val broadcast = network or mask.inv()
            return (network + 1 until broadcast)
                .filterNot { it == local }
                .map(::formatIpv4)
        }

        private fun formatIpv4(value: Int): String = listOf(24, 16, 8, 0)
            .joinToString(".") { shift -> ((value ushr shift) and 0xff).toString() }
    }
}
package app.remotio.tv

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.SystemClock
import app.remotio.tv.firetv.FireTvRemote
import app.remotio.tv.roku.RokuRemote
import app.remotio.tv.roku.parseSsdpLocationHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.util.Collections
import kotlin.coroutines.resume

/** Finds Roku TVs (SSDP) and Fire TVs (mDNS) on the phone's Wi-Fi network. */
class TvDiscovery(context: Context) {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)
    private val nsd = context.applicationContext.getSystemService(NsdManager::class.java)

    suspend fun discover(): List<Tv> = withContext(Dispatchers.IO) {
        val lock = wifi?.createMulticastLock("remotio-discovery")?.apply {
            setReferenceCounted(false)
            acquire()
        }
        try {
            val rokus = async { findRokus() }
            val fireTvs = async { findFireTvs() }
            rokus.await() + fireTvs.await()
        } finally {
            lock?.release()
        }
    }

    private suspend fun findRokus(): List<Tv> = coroutineScope {
        searchSsdpForRokus()
            .map { host -> async { runCatching { RokuRemote(host).deviceInfo() }.getOrNull() } }
            .awaitAll()
            .filterNotNull()
    }

    private fun searchSsdpForRokus(): Set<String> = DatagramSocket().use { socket ->
        socket.soTimeout = 250
        val request = ROKU_M_SEARCH.toByteArray()
        val group = InetAddress.getByName(SSDP_ADDRESS)
        // UDP can drop packets; asking twice is what Roku's own apps do.
        repeat(2) { socket.send(DatagramPacket(request, request.size, group, SSDP_PORT)) }

        val hosts = linkedSetOf<String>()
        val buffer = ByteArray(2048)
        val deadline = SystemClock.elapsedRealtime() + SEARCH_MS
        while (SystemClock.elapsedRealtime() < deadline) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (e: SocketTimeoutException) {
                continue
            }
            parseSsdpLocationHost(String(packet.data, 0, packet.length))?.let(hosts::add)
        }
        hosts
    }

    private suspend fun findFireTvs(): List<Tv> =
        browse(FIRE_TV_SERVICE_TYPE)
            // Resolve one at a time: before Android 14, NsdManager rejects concurrent resolves.
            .mapNotNull { resolve(it)?.toFireTv() }
            .distinctBy { it.host }
            .filter { FireTvRemote.isFireTv(it.host) }

    private suspend fun browse(serviceType: String): List<NsdServiceInfo> {
        val found = Collections.synchronizedList(mutableListOf<NsdServiceInfo>())
        val listener = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                found += info
            }

            override fun onServiceLost(info: NsdServiceInfo) {}
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        nsd.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        try {
            delay(SEARCH_MS)
        } finally {
            // Throws if discovery never started; nothing to stop in that case.
            runCatching { nsd.stopServiceDiscovery(listener) }
        }
        return found.toList()
    }

    @Suppress("DEPRECATION") // registerServiceInfoCallback replaces this, but only from Android 14.
    private suspend fun resolve(info: NsdServiceInfo): NsdServiceInfo? = suspendCancellableCoroutine { continuation ->
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onServiceResolved(resolved: NsdServiceInfo) = continuation.resume(resolved)
            override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) = continuation.resume(null)
        })
    }

    @Suppress("DEPRECATION") // NsdServiceInfo.host; hostAddresses needs Android 14.
    private fun NsdServiceInfo.toFireTv(): Tv? {
        val address = host?.hostAddress ?: return null
        // Fire TVs put the name the owner gave them ("Living Room Fire TV") in the TXT record's "n" field.
        val name = attributes["n"]?.decodeToString()?.ifBlank { null } ?: DEFAULT_FIRE_TV_NAME
        return Tv(Platform.FireTv, address, name)
    }

    private companion object {
        const val SEARCH_MS = 3_000L
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val ROKU_M_SEARCH = "M-SEARCH * HTTP/1.1\r\n" +
            "Host: 239.255.255.250:1900\r\n" +
            "Man: \"ssdp:discover\"\r\n" +
            "ST: roku:ecp\r\n" +
            "MX: 2\r\n\r\n"
        const val FIRE_TV_SERVICE_TYPE = "_amzn-wplay._tcp"
        const val DEFAULT_FIRE_TV_NAME = "Fire TV"
    }
}

package app.remotio.tv.firetv

import app.remotio.tv.CommandRefusedException
import app.remotio.tv.HttpResponse
import app.remotio.tv.RemoteKey
import app.remotio.tv.TvHttpException
import app.remotio.tv.TvRemote
import app.remotio.tv.openHttp
import app.remotio.tv.send
import app.remotio.tv.urlHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext

/**
 * Talks to a Fire TV the way Amazon's Fire TV phone app does: starting the "FireTVRemote" DIAL app
 * on port 8009 brings up an HTTPS API on port 8080, and a one-time PIN shown on the TV buys a client
 * token. Amazon doesn't document this API, so a Fire OS update could change it.
 */
class FireTvRemote(
    private val host: String,
    private val token: String? = null,
    pinnedCertSha256: String? = null,
) : TvRemote {

    private val trust = PinnedCertTrustManager(pinnedCertSha256)
    private val sslSocketFactory = SSLContext.getInstance("TLS")
        .apply { init(null, arrayOf(trust), null) }
        .socketFactory

    // The certificate names the device, not its IP, so there is no hostname to check.
    // The pinned certificate is what proves we're talking to the TV we paired with.
    private val hostnameVerifier = HostnameVerifier { _, session ->
        session.peerCertificates.firstOrNull()?.sha256() == trust.seenSha256
    }

    /** SHA-256 of the certificate the TV presented on the last HTTPS request. */
    val certSha256: String? get() = trust.seenSha256

    override suspend fun press(key: RemoteKey) {
        val command = key.command ?: throw IllegalArgumentException("Fire TV has no $key button")
        checkCommand(api(command.path, command.body))
    }

    // The endpoint exists on the device (it answers "missing client token", not "unknown uri"),
    // but the body shape is a best guess until checked against a paired TV.
    override suspend fun type(text: String) {
        checkCommand(api("/v1/FireTV/text", JSONObject().put("text", text).toString()))
    }

    private fun checkCommand(response: HttpResponse) {
        if (response.code == 401 || response.code == 403) throw CommandRefusedException("Fire TV rejected our token")
        if (!response.isSuccess) throw TvHttpException(response.code, response.body)
    }

    /** Asks the TV to put a pairing PIN on screen. */
    suspend fun showPin() {
        val response = api("/v1/FireTV/pin/display", JSONObject().put("friendlyName", CLIENT_NAME).toString())
        if (!response.isSuccess) throw IOException("Fire TV PIN display failed: HTTP ${response.code} ${response.body}")
    }

    /** Trades the PIN shown on the TV for a client token. */
    suspend fun verifyPin(pin: String): String {
        val response = api("/v1/FireTV/pin/verify", JSONObject().put("pin", pin).toString())
        if (!response.isSuccess) throw CommandRefusedException("Fire TV rejected PIN: HTTP ${response.code}")
        return runCatching { JSONObject(response.body).getString("description") }.getOrNull()
            ?: throw IOException("Unexpected pairing response: ${response.body}")
    }

    /** Sends an API request, starting the TV's remote service first if it has gone to sleep. */
    private suspend fun api(path: String, body: String?): HttpResponse = withContext(Dispatchers.IO) {
        try {
            request(path, body)
        } catch (e: ConnectException) {
            wake(host)
            request(path, body)
        }
    }

    private fun request(path: String, body: String?): HttpResponse {
        val connection = URL("https://${urlHost(host)}:$API_PORT$path").openConnection() as HttpsURLConnection
        connection.sslSocketFactory = sslSocketFactory
        connection.hostnameVerifier = hostnameVerifier
        connection.setRequestProperty("X-Api-Key", API_KEY)
        token?.let { connection.setRequestProperty("X-Client-Token", it) }
        return connection.send("POST", body, "application/json; charset=utf-8")
    }

    private class Command(val path: String, val body: String? = null)

    companion object {
        private const val DIAL_PORT = 8009
        private const val API_PORT = 8080
        /** The fixed key Amazon's own app sends; it's the same for every Fire TV. */
        private const val API_KEY = "0987654321"
        private const val CLIENT_NAME = "Remotio"

        private val RemoteKey.command: Command?
            get() = when (this) {
                RemoteKey.Up -> action("dpad_up")
                RemoteKey.Down -> action("dpad_down")
                RemoteKey.Left -> action("dpad_left")
                RemoteKey.Right -> action("dpad_right")
                RemoteKey.Select -> action("select")
                RemoteKey.Home -> action("home")
                RemoteKey.Back -> action("back")
                RemoteKey.Options -> action("menu")
                // There's no power-on: a sleeping Fire TV stops answering until it's woken by its own remote or HDMI-CEC.
                RemoteKey.Power -> action("sleep")
                // Not confirmed against a device yet. Fire TV forwards these to the TV over HDMI-CEC.
                RemoteKey.VolumeUp -> action("volume_up")
                RemoteKey.VolumeDown -> action("volume_down")
                RemoteKey.Mute -> action("mute")
                RemoteKey.PlayPause -> Command("/v1/media?action=play")
                RemoteKey.Rewind -> scan("back")
                RemoteKey.FastForward -> scan("forward")
                RemoteKey.InstantReplay, RemoteKey.ChannelUp, RemoteKey.ChannelDown -> null
            }

        val SUPPORTED_KEYS: Set<RemoteKey> = RemoteKey.entries.filter { it.command != null }.toSet()

        private fun action(name: String) = Command("/v1/FireTV?action=$name")

        private fun scan(direction: String) = Command(
            "/v1/media?action=scan",
            """{"direction":"$direction","keyAction":{"keyActionType":"keyDown"}}""",
        )

        private fun dialAppUrl(host: String) = "http://${urlHost(host)}:$DIAL_PORT/apps/FireTVRemote"

        /** True if [host] runs Fire TV's remote-control DIAL app. Phones with Amazon apps advertise the same mDNS service. */
        suspend fun isFireTv(host: String): Boolean = withContext(Dispatchers.IO) {
            runCatching { openHttp(dialAppUrl(host)).send("GET").isSuccess }.getOrDefault(false)
        }

        /** Starts the TV's remote-control service and waits for its HTTPS port to open. */
        private suspend fun wake(host: String) {
            val response = openHttp(dialAppUrl(host)).send("POST", contentType = "text/plain; charset=utf-8")
            if (!response.isSuccess) throw IOException("Fire TV wake failed: HTTP ${response.code}")
            repeat(20) {
                if (isPortOpen(host, API_PORT)) return
                delay(250)
            }
            throw ConnectException("Fire TV remote service didn't start")
        }

        private fun isPortOpen(host: String, port: Int): Boolean =
            runCatching { Socket().use { it.connect(InetSocketAddress(host, port), 500) } }.isSuccess
    }
}

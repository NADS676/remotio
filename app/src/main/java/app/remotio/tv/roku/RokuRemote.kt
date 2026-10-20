package app.remotio.tv.roku

import app.remotio.tv.CommandRefusedException
import app.remotio.tv.LiveTypingRemote
import app.remotio.tv.Platform
import app.remotio.tv.RemoteKey
import app.remotio.tv.Tv
import app.remotio.tv.TvHttpException
import app.remotio.tv.openHttp
import app.remotio.tv.send
import app.remotio.tv.urlHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import java.net.URLEncoder
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Talks to a Roku TV over its External Control Protocol: plain HTTP on port 8060, no pairing.
 * https://developer.roku.com/docs/developer-program/dev-tools/external-control-api.md
 */
class RokuRemote(private val host: String) : LiveTypingRemote {

    override suspend fun press(key: RemoteKey) = keypress(key.ecpName)

    override suspend fun type(text: String) {
        for (codePoint in text.codePoints().toArray()) keypress(literalKey(String(Character.toChars(codePoint))))
    }

    override suspend fun backspace(count: Int) = repeat(count) { keypress("Backspace") }

    override suspend fun submit() = keypress("Enter")

    suspend fun deviceInfo(): Tv = withContext(Dispatchers.IO) {
        val response = openHttp(url("/query/device-info")).send("GET")
        if (!response.isSuccess) throw TvHttpException(response.code, response.body)
        parseDeviceInfo(host, response.body)
    }

    private suspend fun keypress(ecpName: String) {
        withContext(Dispatchers.IO) {
            val response = openHttp(url("/keypress/$ecpName")).send("POST")
            // 403 means "Control by mobile apps" is switched off on the TV.
            if (response.code == 403) throw CommandRefusedException("Roku refused $ecpName")
            if (!response.isSuccess) throw TvHttpException(response.code, response.body)
        }
    }

    private fun url(path: String) = "http://${urlHost(host)}:$ECP_PORT$path"

    companion object {
        const val ECP_PORT = 8060
    }
}

private val RemoteKey.ecpName: String
    get() = when (this) {
        RemoteKey.Power -> "Power"
        RemoteKey.Home -> "Home"
        RemoteKey.Back -> "Back"
        RemoteKey.Options -> "Info"
        RemoteKey.Up -> "Up"
        RemoteKey.Down -> "Down"
        RemoteKey.Left -> "Left"
        RemoteKey.Right -> "Right"
        RemoteKey.Select -> "Select"
        RemoteKey.InstantReplay -> "InstantReplay"
        RemoteKey.Rewind -> "Rev"
        RemoteKey.PlayPause -> "Play"
        RemoteKey.FastForward -> "Fwd"
        RemoteKey.VolumeUp -> "VolumeUp"
        RemoteKey.VolumeDown -> "VolumeDown"
        RemoteKey.Mute -> "VolumeMute"
        RemoteKey.ChannelUp -> "ChannelUp"
        RemoteKey.ChannelDown -> "ChannelDown"
    }

/** ECP types any character with a "Lit_" key, URL-encoded: "Lit_a", "Lit_%20", "Lit_%E2%82%AC". */
internal fun literalKey(character: String): String =
    "Lit_" + URLEncoder.encode(character, "UTF-8").replace("+", "%20")

internal fun parseDeviceInfo(host: String, xml: String): Tv {
    val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(InputSource(StringReader(xml)))
    fun text(tag: String) = doc.getElementsByTagName(tag).item(0)?.textContent?.trim()?.ifEmpty { null }
    // user-device-name is what the owner typed in on the TV ("Living Room"); the rest are fallbacks.
    val name = text("user-device-name") ?: text("friendly-device-name") ?: text("model-name") ?: host
    return Tv(Platform.Roku, host, name)
}

/** Returns the host from a Roku's SSDP reply ("LOCATION: http://192.168.0.20:8060/"), or null for any other device. */
internal fun parseSsdpLocationHost(reply: String): String? {
    if (!reply.contains("roku:ecp", ignoreCase = true)) return null
    val location = reply.lineSequence()
        .firstOrNull { it.startsWith("location:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?: return null
    return runCatching { URI(location).host }.getOrNull()
}

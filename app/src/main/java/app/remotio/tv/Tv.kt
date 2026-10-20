package app.remotio.tv

import app.remotio.tv.firetv.FireTvRemote
import app.remotio.tv.roku.RokuRemote
import java.io.IOException

enum class Platform { Roku, FireTv }

/** A TV or streaming stick Remotio can reach on the local network. */
data class Tv(
    val platform: Platform,
    val host: String,
    val name: String,
    /** Fire TV only: client token handed out by PIN pairing. */
    val token: String? = null,
    /** Fire TV only: SHA-256 of the certificate seen while pairing, pinned for later connections. */
    val certSha256: String? = null,
) {
    val id: String get() = "${platform.name}@$host"
    val needsPairing: Boolean get() = platform == Platform.FireTv && token == null
}

/** The buttons on Remotio's remote. Each [TvRemote] maps them onto its own protocol. */
enum class RemoteKey {
    Power, Home, Back, Options,
    Up, Down, Left, Right, Select,
    InstantReplay, Rewind, PlayPause, FastForward,
    VolumeUp, VolumeDown, Mute,
    ChannelUp, ChannelDown,
}

interface TvRemote {
    /** Sends one button press. Throws [IOException] when the TV can't be reached. */
    suspend fun press(key: RemoteKey)

    /** Types [text] into whatever text box is open on the TV. */
    suspend fun type(text: String)
}

/** A remote that can also delete and submit, so edits on the phone can be mirrored on the TV as you type. */
interface LiveTypingRemote : TvRemote {
    suspend fun backspace(count: Int)

    suspend fun submit()
}

/** The TV answered but refused: Roku network control is switched off, or a Fire TV pairing was revoked. */
class CommandRefusedException(message: String) : IOException(message)

/** The TV was reachable but answered with an HTTP error, so it isn't a connection problem. */
class TvHttpException(val code: Int, val body: String) : IOException("HTTP $code: $body")

fun Tv.remote(): TvRemote = when (platform) {
    Platform.Roku -> RokuRemote(host)
    Platform.FireTv -> FireTvRemote(host, token, certSha256)
}

val Platform.supportedKeys: Set<RemoteKey>
    get() = when (this) {
        Platform.Roku -> RemoteKey.entries.toSet()
        Platform.FireTv -> FireTvRemote.SUPPORTED_KEYS
    }

/** Roku's API edits the TV's text box a key at a time. Fire TV's text API is unverified, so it gets Send only. */
val Platform.supportsLiveTyping: Boolean
    get() = this == Platform.Roku

/**
 * What to send so a TV text box showing [from] ends up showing [to]: how many backspaces, then what to
 * type. Backspaces are counted in characters as the TV sees them, so an emoji is one.
 */
fun textEdit(from: String, to: String): Pair<Int, String> {
    val common = from.commonPrefixWith(to).length
    return from.codePointCount(common, from.length) to to.substring(common)
}

/** Turns whatever the user typed ("http://192.168.0.20:8060/") into a bare host. */
fun normalizeHost(input: String): String =
    input.trim()
        .substringAfter("://")
        .substringBefore('/')
        .let { if (it.count { c -> c == ':' } == 1) it.substringBefore(':') else it }

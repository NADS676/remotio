package app.remotio.tv

import java.net.HttpURLConnection
import java.net.URL

internal class HttpResponse(val code: Int, val body: String) {
    val isSuccess: Boolean get() = code in 200..299
}

internal fun openHttp(url: String): HttpURLConnection = URL(url).openConnection() as HttpURLConnection

/** Wraps IPv6 literals in brackets so the host can go straight into a URL. */
internal fun urlHost(host: String): String = if (':' in host) "[$host]" else host

/** Blocking request. Run it on [kotlinx.coroutines.Dispatchers.IO]. */
internal fun HttpURLConnection.send(
    method: String,
    body: String? = null,
    contentType: String? = null,
): HttpResponse {
    connectTimeout = TIMEOUT_MS
    readTimeout = TIMEOUT_MS
    requestMethod = method
    contentType?.let { setRequestProperty("Content-Type", it) }
    if (method == "POST") {
        val bytes = body.orEmpty().toByteArray()
        doOutput = true
        setFixedLengthStreamingMode(bytes.size)
        outputStream.use { it.write(bytes) }
    }
    val code = responseCode
    // Read to the end (rather than disconnect) so the socket goes back to the keep-alive pool;
    // reusing it keeps button presses snappy.
    val stream = if (code >= 400) errorStream else inputStream
    val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    return HttpResponse(code, text)
}

private const val TIMEOUT_MS = 2_500

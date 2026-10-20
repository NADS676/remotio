package app.remotio.tv.firetv

import java.security.MessageDigest
import java.security.cert.Certificate
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Fire TVs present a per-device certificate from Amazon's private CA, which Android doesn't trust.
 * We accept whichever certificate the TV shows while pairing, then refuse any other one afterwards
 * (trust on first use).
 */
internal class PinnedCertTrustManager(private val pinnedSha256: String?) : X509TrustManager {

    @Volatile
    var seenSha256: String? = null
        private set

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val sha256 = chain.firstOrNull()?.sha256() ?: throw CertificateException("Empty certificate chain")
        if (pinnedSha256 != null && sha256 != pinnedSha256) {
            throw CertificateException("Fire TV certificate changed since pairing")
        }
        seenSha256 = sha256
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
        throw CertificateException("Client certificates aren't used")

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

internal fun Certificate.sha256(): String =
    MessageDigest.getInstance("SHA-256").digest(encoded).joinToString("") { "%02x".format(it) }

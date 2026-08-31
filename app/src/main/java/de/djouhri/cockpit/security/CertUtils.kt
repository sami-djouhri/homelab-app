package de.djouhri.cockpit.security

import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/**
 * Reine (Android-freie) Helfer rund um X.509-PEM und Fingerprints. Bewusst ohne
 * AndroidKeyStore-Abhaengigkeit, damit die Logik auf der JVM unit-testbar ist.
 */
object CertUtils {

    private val X509 = CertificateFactory.getInstance("X.509")

    /** Parst eine PEM-Kette (ein oder mehrere `-----BEGIN CERTIFICATE-----`-Bloecke). */
    fun parsePemChain(pem: String): List<X509Certificate> {
        val certs = X509.generateCertificates(ByteArrayInputStream(pem.toByteArray(Charsets.UTF_8)))
        return certs.filterIsInstance<X509Certificate>()
    }

    /** SHA-256-Fingerprint eines Zertifikats als Doppelpunkt-Hex in Grossschrift. */
    fun sha256Fingerprint(cert: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }

    /**
     * Prueft, ob der erwartete Fingerprint (aus dem QR) zum ersten Zertifikat der
     * PEM-Kette passt. Trenner/Gross-Kleinschreibung werden normalisiert, sodass
     * `A6:9D:...`, `a69d...` und `A6 9D ...` gleich behandelt werden. Ein leerer
     * Erwartungswert gilt als „kein Pinning verlangt" → true.
     */
    fun fingerprintMatches(expected: String?, caPem: String): Boolean {
        if (expected.isNullOrBlank()) return true
        val cert = parsePemChain(caPem).firstOrNull() ?: return false
        return normalize(sha256Fingerprint(cert)) == normalize(expected)
    }

    private fun normalize(fp: String): String =
        fp.filter { it.isLetterOrDigit() }.lowercase()
}

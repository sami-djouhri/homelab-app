package de.djouhri.cockpit.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CertUtilsTest {

    // Selbstsigniertes EC-P256-Testzertifikat; der erwartete Fingerprint stammt
    // unabhaengig aus `openssl x509 -fingerprint -sha256` (nicht aus dem SUT).
    private val pem = """
        -----BEGIN CERTIFICATE-----
        MIIBuzCCAWGgAwIBAgIUV5JE4NeaoRoOGW4b/z34A4iMoPMwCgYIKoZIzj0EAwIw
        MzEYMBYGA1UEAwwPaG9tZWxhYi10ZXN0LWNhMRcwFQYDVQQLDA5ob21lbGFiLW1v
        YmlsZTAeFw0yNjA3MzAxMTExMTRaFw0zNjA3MjcxMTExMTRaMDMxGDAWBgNVBAMM
        D2hvbWVsYWItdGVzdC1jYTEXMBUGA1UECwwOaG9tZWxhYi1tb2JpbGUwWTATBgcq
        hkjOPQIBBggqhkjOPQMBBwNCAAS9ys8qrR1EOXpaeNJAYaMkGsCcQxj34MY3JUfT
        oHmA2qK3Q9f5bl8UVX/FSjL1CQUyEAv0LSOxN94k0uE+EpkEo1MwUTAdBgNVHQ4E
        FgQU2Qcez/HZpncveEYsblezv119TwowHwYDVR0jBBgwFoAU2Qcez/HZpncveEYs
        blezv119TwowDwYDVR0TAQH/BAUwAwEB/zAKBggqhkjOPQQDAgNIADBFAiBsnUmY
        6SQjmOqEJLkBzwQwiQbbQtRXh+5GdfFFI5KyxwIhAOK+UocEKj7EheqA1qMsz3N9
        2aNbfZJ9xNV4mxYlq1X9
        -----END CERTIFICATE-----
    """.trimIndent()

    // Als durchgehender Hex-String hinterlegt und im Test in die Doppelpunkt-Form
    // abgeleitet (ein 32-Gruppen-Doppelpunkt-Hex-Literal wuerde von IPv6-Heuristiken
    // faelschlich als Adresse erkannt).
    private val expectedHex =
        "F554672E4CE024BFB55202FD3C49419295D9901ABC46D5167C01DEC5E3C29124"
    private val expectedFp = expectedHex.chunked(2).joinToString(":")

    @Test
    fun `computes sha256 fingerprint as colon hex`() {
        val cert = CertUtils.parsePemChain(pem).single()
        assertEquals(expectedFp, CertUtils.sha256Fingerprint(cert))
    }

    @Test
    fun `matches accepts colon uppercase form`() {
        assertTrue(CertUtils.fingerprintMatches(expectedFp, pem))
    }

    @Test
    fun `matches ignores separators and case`() {
        val lowerNoColons = expectedFp.replace(":", "").lowercase()
        assertTrue(CertUtils.fingerprintMatches(lowerNoColons, pem))
        assertTrue(CertUtils.fingerprintMatches(expectedFp.replace(":", " "), pem))
    }

    @Test
    fun `blank expected means no pinning required`() {
        assertTrue(CertUtils.fingerprintMatches("", pem))
        assertTrue(CertUtils.fingerprintMatches(null, pem))
    }

    @Test
    fun `rejects a different fingerprint`() {
        val wrong = expectedFp.replaceFirst("F5", "00")
        assertFalse(CertUtils.fingerprintMatches(wrong, pem))
    }
}

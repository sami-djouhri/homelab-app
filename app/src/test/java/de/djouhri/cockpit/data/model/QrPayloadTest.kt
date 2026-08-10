package de.djouhri.cockpit.data.model

import de.djouhri.cockpit.data.model.cockpit.QrPayload
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class QrPayloadTest {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun `parses full payload with gateway url and fingerprint`() {
        val raw = """
            {"gateway_url":"http://gateway.example:8140",
             "ca_fingerprint":"A6:9D:F1:12","nonce":"abc123"}
        """.trimIndent()
        val payload = json.decodeFromString<QrPayload>(raw)
        assertEquals("http://gateway.example:8140", payload.gatewayUrl)
        assertEquals("A6:9D:F1:12", payload.caFingerprint)
        assertEquals("abc123", payload.nonce)
    }

    @Test
    fun `parses minimal payload with only nonce`() {
        val payload = json.decodeFromString<QrPayload>("""{"nonce":"only-nonce"}""")
        assertEquals("only-nonce", payload.nonce)
        assertEquals("", payload.gatewayUrl)
        assertEquals("", payload.caFingerprint)
    }

    @Test
    fun `ignores unknown extra fields`() {
        val payload = json.decodeFromString<QrPayload>(
            """{"nonce":"n","extra":"ignored","version":2}""",
        )
        assertEquals("n", payload.nonce)
    }
}

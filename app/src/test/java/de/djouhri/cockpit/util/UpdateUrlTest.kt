package de.djouhri.cockpit.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateUrlTest {

    private val gateway = "http://gateway.example:8140"

    @Test
    fun `resolves a relative path against the gateway`() {
        val result = UpdateUrl.resolve(gateway, "/cockpit.apk")
        assertEquals("http://gateway.example:8140/cockpit.apk", result.getOrThrow())
    }

    @Test
    fun `resolves a relative path without leading slash`() {
        val result = UpdateUrl.resolve(gateway, "cockpit.apk")
        assertEquals("http://gateway.example:8140/cockpit.apk", result.getOrThrow())
    }

    @Test
    fun `accepts an absolute same-origin url`() {
        val result = UpdateUrl.resolve(gateway, "http://gateway.example:8140/dl/app.apk")
        assertTrue(result.isSuccess)
    }

    @Test
    fun `rejects a foreign host`() {
        val result = UpdateUrl.resolve(gateway, "http://evil.example:8140/app.apk")
        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects a different port`() {
        val result = UpdateUrl.resolve(gateway, "http://gateway.example:9999/app.apk")
        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects a scheme change`() {
        val result = UpdateUrl.resolve(gateway, "https://gateway.example:8140/app.apk")
        assertTrue(result.isFailure)
    }

    @Test
    fun `rejects an empty apk url`() {
        assertTrue(UpdateUrl.resolve(gateway, "   ").isFailure)
    }
}

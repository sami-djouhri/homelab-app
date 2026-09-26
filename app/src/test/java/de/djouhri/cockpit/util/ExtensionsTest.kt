package de.djouhri.cockpit.util

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

class ExtensionsTest {

    @Test
    fun `io exception hints at the vpn`() {
        val msg = IOException("timeout").userMessage()
        assertTrue(msg.contains("VPN"))
    }

    @Test
    fun `401 maps to re-pair hint`() {
        val body = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val ex = HttpException(Response.error<Any>(401, body))
        assertTrue(ex.userMessage().contains("koppeln"))
    }

    @Test
    fun `5xx maps to server error`() {
        val body = "".toResponseBody("text/plain".toMediaTypeOrNull())
        val ex = HttpException(Response.error<Any>(503, body))
        assertTrue(ex.userMessage().contains("Serverfehler"))
    }

    @Test
    fun `formats bytes in gib and mib`() {
        assertEquals("0 B", 0L.formatBytes())
        assertEquals("2.0 GiB", (2L * 1024 * 1024 * 1024).formatBytes())
        assertEquals("512 MiB", (512L * 1024 * 1024).formatBytes())
    }

    @Test
    fun `formats percent with fallback`() {
        assertEquals("–", (null as Double?).formatPercent())
        assertEquals("50 %", 50.0.formatPercent())
    }
}

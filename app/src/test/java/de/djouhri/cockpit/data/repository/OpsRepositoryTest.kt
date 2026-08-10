package de.djouhri.cockpit.data.repository

import de.djouhri.cockpit.data.api.OpsApi
import de.djouhri.cockpit.data.demo.DemoData
import de.djouhri.cockpit.data.demo.DemoModeManager
import de.djouhri.cockpit.data.model.cockpit.LogsResponse
import de.djouhri.cockpit.data.model.cockpit.ServiceActionResult
import de.djouhri.cockpit.data.model.cockpit.ServiceSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OpsRepositoryTest {

    private class FakeOpsApi : OpsApi {
        var servicesCalls = 0
        var lastAction: String? = null
        val canned = listOf(ServiceSummary(host = "h1", name = "svc", status = "running"))

        override suspend fun services(host: String?): List<ServiceSummary> {
            servicesCalls++
            return canned
        }

        override suspend fun action(host: String, name: String, action: String): ServiceActionResult {
            lastAction = action
            return ServiceActionResult(name = name, host = host, status = "running")
        }

        override suspend fun logs(host: String, name: String, lines: Int): LogsResponse =
            LogsResponse(name = name, host = host, logs = "real-logs")
    }

    @Test
    fun `demo mode returns demo data without touching the api`() = runTest {
        val api = FakeOpsApi()
        val demo = DemoModeManager().apply { enable() }
        val repo = OpsRepository(api, demo)

        val services = repo.services().getOrThrow()

        assertEquals(DemoData.services, services)
        assertEquals(0, api.servicesCalls)
    }

    @Test
    fun `demo action is simulated locally`() = runTest {
        val demo = DemoModeManager().apply { enable() }
        val repo = OpsRepository(FakeOpsApi(), demo)

        assertEquals("exited", repo.action("h", "svc", "stop").getOrThrow().status)
        assertEquals("running", repo.action("h", "svc", "start").getOrThrow().status)
    }

    @Test
    fun `non-demo delegates to the api`() = runTest {
        val api = FakeOpsApi()
        val repo = OpsRepository(api, DemoModeManager())

        val services = repo.services().getOrThrow()

        assertEquals(1, api.servicesCalls)
        assertEquals("svc", services.single().name)
        assertFalse(services === DemoData.services)

        repo.action("h", "svc", "restart")
        assertEquals("restart", api.lastAction)
    }

    @Test
    fun `demo logs are canned`() = runTest {
        val repo = OpsRepository(FakeOpsApi(), DemoModeManager().apply { enable() })
        assertTrue(repo.logs("h", "svc").getOrThrow().logs.contains("demo"))
    }
}

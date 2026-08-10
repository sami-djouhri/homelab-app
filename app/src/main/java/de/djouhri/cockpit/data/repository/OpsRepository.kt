package de.djouhri.cockpit.data.repository

import de.djouhri.cockpit.data.api.OpsApi
import de.djouhri.cockpit.data.demo.DemoData
import de.djouhri.cockpit.data.demo.DemoModeManager
import de.djouhri.cockpit.data.model.cockpit.LogsResponse
import de.djouhri.cockpit.data.model.cockpit.ServiceActionResult
import de.djouhri.cockpit.data.model.cockpit.ServiceSummary
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpsRepository @Inject constructor(
    private val api: OpsApi,
    private val demo: DemoModeManager,
) {
    suspend fun services(host: String? = null): Result<List<ServiceSummary>> {
        if (demo.isActive) {
            return Result.success(DemoData.services.filter { host == null || it.host == host })
        }
        return runCatching { api.services(host) }
    }

    suspend fun action(host: String, name: String, action: String): Result<ServiceActionResult> {
        if (demo.isActive) {
            val status = if (action == "stop") "exited" else "running"
            return Result.success(ServiceActionResult(name = name, host = host, status = status))
        }
        return runCatching { api.action(host, name, action) }
    }

    suspend fun logs(host: String, name: String, lines: Int = 200): Result<LogsResponse> {
        if (demo.isActive) return Result.success(DemoData.logs(name))
        return runCatching { api.logs(host, name, lines) }
    }
}

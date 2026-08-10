package de.djouhri.cockpit.data.model.cockpit

import kotlinx.serialization.Serializable

/** Ein Container, wie ihn /v1/ops/services liefert. */
@Serializable
data class ServiceSummary(
    val host: String = "",
    val name: String,
    val status: String = "unknown",
    val health: String? = null,
    val image: String? = null,
    val ports: List<String> = emptyList(),
) {
    /** running / exited / restarting / … normalisiert fuer die UI-Einordnung. */
    val isRunning: Boolean get() = status.equals("running", ignoreCase = true)
    val isUnhealthy: Boolean get() = health?.equals("unhealthy", ignoreCase = true) == true
}

/** Antwort auf eine start/stop/restart-Aktion (dev-portal-Durchreichung). */
@Serializable
data class ServiceActionResult(
    val name: String? = null,
    val host: String? = null,
    val status: String? = null,
)

@Serializable
data class LogsResponse(
    val name: String? = null,
    val host: String? = null,
    val logs: String = "",
)

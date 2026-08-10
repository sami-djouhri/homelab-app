package de.djouhri.cockpit.data.demo

import de.djouhri.cockpit.data.model.cockpit.DashboardSummary
import de.djouhri.cockpit.data.model.cockpit.HostHealth
import de.djouhri.cockpit.data.model.cockpit.InboxCounts
import de.djouhri.cockpit.data.model.cockpit.InboxItem
import de.djouhri.cockpit.data.model.cockpit.InboxList
import de.djouhri.cockpit.data.model.cockpit.LogsResponse
import de.djouhri.cockpit.data.model.cockpit.ServiceSummary

/**
 * Statische, bewusst neutrale Beispieldaten fuer den Demo-Modus. Host-Namen sind
 * generisch (`edge`/`app-node`/`compute`) - keine realen Homelab-Details.
 */
object DemoData {

    val services: List<ServiceSummary> = listOf(
        ServiceSummary("edge", "reverse-proxy", "running", "healthy", "caddy:2"),
        ServiceSummary("edge", "vpn-gateway", "running", null, "wireguard:latest"),
        ServiceSummary("edge", "status-page", "running", "healthy", "gatus:v5"),
        ServiceSummary("app-node", "web-portal", "running", "healthy", "ghcr.io/demo/web:1.4.2"),
        ServiceSummary("app-node", "notes-api", "running", "healthy", "ghcr.io/demo/notes:0.9.1"),
        ServiceSummary("app-node", "photo-vault", "restarting", null, "ghcr.io/demo/photos:2.1"),
        ServiceSummary("app-node", "search-index", "running", "unhealthy", "opensearch:2.13"),
        ServiceSummary("compute", "metrics", "running", "healthy", "prom/prometheus:v2.53"),
        ServiceSummary("compute", "dashboards", "running", "healthy", "grafana/grafana:11.1"),
        ServiceSummary("compute", "llm-gateway", "running", "healthy", "ghcr.io/demo/llm-gw:0.6"),
        ServiceSummary("compute", "batch-worker", "exited", null, "ghcr.io/demo/worker:1.0"),
    )

    val hostHealth = HostHealth(
        cpuPercent = 18.0,
        memoryTotal = 16L * 1024 * 1024 * 1024,
        memoryUsed = 6L * 1024 * 1024 * 1024,
        memoryPercent = 37.0,
        diskTotal = 512L * 1024 * 1024 * 1024,
        diskUsed = 233L * 1024 * 1024 * 1024,
        diskPercent = 45.0,
        cpuTemp = 47.0,
        load1m = 0.72,
        load5m = 0.65,
        load15m = 0.58,
    )

    val inboxCounts = InboxCounts(open = 3, snoozed = 1, done = 12, archived = 40, totalVisible = 4)

    val summary = DashboardSummary(health = hostHealth, inboxCounts = inboxCounts)

    val inboxItems: List<InboxItem> = listOf(
        InboxItem(
            externalId = "demo-1",
            source = "monitoring",
            title = "search-index meldet unhealthy",
            detail = "Cluster-Status yellow seit 12 min - 1 Shard nicht zugewiesen.",
            severity = "warning",
            ageHours = 0.4,
        ),
        InboxItem(
            externalId = "demo-2",
            source = "backup",
            title = "Off-Site-Snapshot abgeschlossen",
            detail = "restic: 4,1 GiB neu, 0 Fehler.",
            severity = "info",
            ageHours = 6.0,
        ),
        InboxItem(
            externalId = "demo-3",
            source = "cert",
            title = "TLS-Zertifikat laeuft in 12 Tagen ab",
            detail = "status.example.org - Auto-Renewal aktiv, nur zur Info.",
            severity = "info",
            ageHours = 20.0,
        ),
    )

    val inbox = InboxList(items = inboxItems, counts = inboxCounts)

    fun logs(name: String): LogsResponse = LogsResponse(
        name = name,
        host = "demo",
        logs = buildString {
            appendLine("2026-01-01T09:00:01Z INFO  $name gestartet (demo)")
            appendLine("2026-01-01T09:00:02Z INFO  Konfiguration geladen")
            appendLine("2026-01-01T09:00:03Z INFO  Lausche auf :8080")
            appendLine("2026-01-01T09:14:55Z WARN  Upstream langsam (312 ms)")
            appendLine("2026-01-01T09:15:00Z INFO  Healthcheck ok")
        },
    )
}

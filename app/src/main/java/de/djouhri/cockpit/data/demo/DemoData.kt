package de.djouhri.cockpit.data.demo

import de.djouhri.cockpit.data.model.cockpit.DashboardSummary
import de.djouhri.cockpit.data.model.cockpit.HostHealth
import de.djouhri.cockpit.data.model.cockpit.HostRollup
import de.djouhri.cockpit.data.model.cockpit.InboxCounts
import de.djouhri.cockpit.data.model.cockpit.InboxItem
import de.djouhri.cockpit.data.model.cockpit.InboxList
import de.djouhri.cockpit.data.model.cockpit.LogsResponse
import de.djouhri.cockpit.data.model.cockpit.ServiceSummary
import de.djouhri.cockpit.data.model.cockpit.ServiceZustand

/**
 * Statische, bewusst neutrale Beispieldaten fuer den Demo-Modus. Host-Namen sind
 * generisch (`edge`/`app-node`/`compute`) - keine realen Homelab-Details.
 */
object DemoData {

    // Die Beispiele decken bewusst alle fuenf Zustaende ab: eine Demo, in der
    // nur laufende Container vorkommen, zeigt genau das nicht, worum es der App
    // geht (den Unterschied zwischen „aus" und „ausgefallen").
    val services: List<ServiceSummary> = listOf(
        ServiceSummary("edge", "reverse-proxy", "running", "healthy", "caddy:2", expectedState = "", inControlMap = true),
        ServiceSummary("edge", "vpn-gateway", "running", null, "wireguard:latest", expectedState = "", inControlMap = true),
        ServiceSummary("edge", "status-page", "running", "healthy", "gatus:v5", expectedState = "", inControlMap = true),
        ServiceSummary("app-node", "web-portal", "running", "healthy", "ghcr.io/demo/web:1.4.2", expectedState = "", inControlMap = true),
        ServiceSummary("app-node", "notes-api", "running", "healthy", "ghcr.io/demo/notes:0.9.1", expectedState = "", inControlMap = true),
        // Neustartschleife: bleibt ein Befund, egal was die control-map sagt.
        ServiceSummary("app-node", "photo-vault", "restarting", null, "ghcr.io/demo/photos:2.1", expectedState = "", inControlMap = true),
        ServiceSummary("app-node", "search-index", "running", "unhealthy", "opensearch:2.13", expectedState = "", inControlMap = true),
        // Soll laufen, tut es nicht. Der einzige echte Ausfall in der Demo.
        ServiceSummary("app-node", "mail-relay", "exited", null, "ghcr.io/demo/relay:3.2", expectedState = "", inControlMap = true),
        ServiceSummary("compute", "metrics", "running", "healthy", "prom/prometheus:v2.53", expectedState = "", inControlMap = true),
        ServiceSummary("compute", "dashboards", "running", "healthy", "grafana/grafana:11.1", expectedState = "", inControlMap = true),
        ServiceSummary("compute", "llm-gateway", "running", "healthy", "ghcr.io/demo/llm-gw:0.6", expectedState = "", inControlMap = true),
        // Einmal-Job und bewusster Rueckfall: sehen aus wie Ausfaelle, sind keine.
        ServiceSummary("compute", "batch-worker", "exited", null, "ghcr.io/demo/worker:1.0", expectedState = "job", inControlMap = true),
        ServiceSummary("compute", "model-fallback", "exited", null, "ghcr.io/demo/llm:0.4", expectedState = "stopped", inControlMap = true),
        // Remote-Host ohne Erwartung in der control-map: nicht beurteilbar.
        ServiceSummary("compute", "altes-archiv", "exited", null, "ghcr.io/demo/archiv:1.0", expectedState = null, inControlMap = null),
    )

    /**
     * Der Zaehlstand wird aus [services] gerechnet, nicht danebengeschrieben.
     * Eine Demo mit zwei Wahrheiten wuerde genau den Fehler vorfuehren, den
     * diese App loswerden soll.
     */
    val hosts: List<HostRollup> = services.groupBy { it.host }.map { (host, liste) ->
        HostRollup(
            name = host,
            erreichbar = true,
            gesamt = liste.size,
            laufend = liste.count { it.zustand == ServiceZustand.LAEUFT },
            auffaellig = liste.count { it.zustand == ServiceZustand.AUFFAELLIG },
            bewusstAus = liste.count { it.zustand == ServiceZustand.BEWUSST_AUS },
            ausgefallen = liste.count { it.zustand == ServiceZustand.AUSGEFALLEN },
            unbewertet = liste.count { it.zustand == ServiceZustand.UNBEWERTET },
        )
    } + listOf(
        // Ein nicht erreichbarer Host liefert keine Zahlen. Eine 0 waere die
        // Aussage „dort laeuft nichts" und damit falsch.
        HostRollup("lab-node", erreichbar = false),
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
            title = "TLS-Zertifikat läuft in 12 Tagen ab",
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

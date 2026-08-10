package de.djouhri.cockpit.data.model.cockpit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Ausschnitt aus /v1/dashboard/summary. Bewusst nur `health` (Host-Metriken) und
 * `inbox_counts`; die uebrigen Summary-Felder werden im Cockpit ignoriert.
 */
@Serializable
data class DashboardSummary(
    val health: HostHealth? = null,
    @SerialName("inbox_counts") val inboxCounts: InboxCounts = InboxCounts(),
)

/** Host-Systemmetriken des Ziel-Hosts (dev-portal /api/system/health). */
@Serializable
data class HostHealth(
    @SerialName("cpu_percent") val cpuPercent: Double? = null,
    @SerialName("memory_total") val memoryTotal: Long? = null,
    @SerialName("memory_used") val memoryUsed: Long? = null,
    @SerialName("memory_percent") val memoryPercent: Double? = null,
    @SerialName("disk_total") val diskTotal: Long? = null,
    @SerialName("disk_used") val diskUsed: Long? = null,
    @SerialName("disk_percent") val diskPercent: Double? = null,
    @SerialName("cpu_temp") val cpuTemp: Double? = null,
    @SerialName("load_1m") val load1m: Double? = null,
    @SerialName("load_5m") val load5m: Double? = null,
    @SerialName("load_15m") val load15m: Double? = null,
)

@Serializable
data class InboxCounts(
    val open: Int = 0,
    val snoozed: Int = 0,
    val done: Int = 0,
    val archived: Int = 0,
    @SerialName("total_visible") val totalVisible: Int = 0,
)

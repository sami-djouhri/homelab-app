package de.djouhri.cockpit.data.model.cockpit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Liste von /v1/inbox (life-ops-Durchreichung). Extra-Felder werden ignoriert. */
@Serializable
data class InboxList(
    val items: List<InboxItem> = emptyList(),
    val counts: InboxCounts = InboxCounts(),
)

@Serializable
data class InboxItem(
    @SerialName("external_id") val externalId: String,
    val source: String = "unknown",
    val kind: String = "",
    val title: String = "",
    val detail: String? = null,
    val severity: String = "info",
    val category: String? = null,
    @SerialName("due_at") val dueAt: String? = null,
    val state: String = "open",
    val note: String? = null,
    @SerialName("age_hours") val ageHours: Double? = null,
)

@Serializable
data class InboxNoteRequest(
    val title: String,
    val content: String = "",
    val tags: List<String> = emptyList(),
    val source: String = "android-cockpit",
)

@Serializable
data class InboxNoteResponse(
    val path: String,
    val slug: String,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class InboxActionRequest(
    val action: String,
    @SerialName("snooze_minutes") val snoozeMinutes: Int? = null,
    val note: String? = null,
)

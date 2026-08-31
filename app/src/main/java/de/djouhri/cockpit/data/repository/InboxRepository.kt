package de.djouhri.cockpit.data.repository

import de.djouhri.cockpit.data.api.InboxApi
import de.djouhri.cockpit.data.demo.DemoData
import de.djouhri.cockpit.data.demo.DemoModeManager
import de.djouhri.cockpit.data.model.cockpit.InboxActionRequest
import de.djouhri.cockpit.data.model.cockpit.InboxList
import de.djouhri.cockpit.data.model.cockpit.InboxNoteRequest
import de.djouhri.cockpit.data.model.cockpit.InboxNoteResponse
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InboxRepository @Inject constructor(
    private val api: InboxApi,
    private val demo: DemoModeManager,
) {
    suspend fun list(limit: Int = 30, includeDone: Boolean = false): Result<InboxList> {
        if (demo.isActive) return Result.success(DemoData.inbox)
        return runCatching { api.list(limit, includeDone) }
    }

    suspend fun createNote(
        title: String,
        content: String = "",
        tags: List<String> = emptyList(),
    ): Result<InboxNoteResponse> {
        if (demo.isActive) {
            return Result.success(
                InboxNoteResponse(path = "demo/$title.md", slug = "demo", createdAt = "2026-01-01T00:00:00Z"),
            )
        }
        return runCatching { api.createNote(InboxNoteRequest(title = title, content = content, tags = tags)) }
    }

    suspend fun action(
        externalId: String,
        action: String,
        snoozeMinutes: Int? = null,
        note: String? = null,
    ): Result<Unit> {
        if (demo.isActive) return Result.success(Unit)
        return runCatching {
            api.action(externalId, InboxActionRequest(action = action, snoozeMinutes = snoozeMinutes, note = note)).close()
        }
    }
}

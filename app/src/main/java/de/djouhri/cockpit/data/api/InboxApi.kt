package de.djouhri.cockpit.data.api

import de.djouhri.cockpit.data.model.cockpit.InboxActionRequest
import de.djouhri.cockpit.data.model.cockpit.InboxList
import de.djouhri.cockpit.data.model.cockpit.InboxNoteRequest
import de.djouhri.cockpit.data.model.cockpit.InboxNoteResponse
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface InboxApi {

    @GET("v1/inbox")
    suspend fun list(
        @Query("limit") limit: Int = 30,
        @Query("include_done") includeDone: Boolean = false,
    ): InboxList

    @POST("v1/inbox/note")
    suspend fun createNote(@Body request: InboxNoteRequest): InboxNoteResponse

    @POST("v1/inbox/{external_id}/action")
    suspend fun action(
        @Path("external_id") externalId: String,
        @Body request: InboxActionRequest,
    ): ResponseBody
}

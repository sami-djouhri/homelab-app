package de.djouhri.cockpit.data.api

import de.djouhri.cockpit.data.model.cockpit.LogsResponse
import de.djouhri.cockpit.data.model.cockpit.ServiceActionResult
import de.djouhri.cockpit.data.model.cockpit.ServiceSummary
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface OpsApi {

    @GET("v1/ops/services")
    suspend fun services(@Query("host") host: String? = null): List<ServiceSummary>

    /** action ist ein Query-Param (kein Body): ?action=start|stop|restart */
    @POST("v1/ops/services/{host}/{name}/action")
    suspend fun action(
        @Path("host") host: String,
        @Path("name") name: String,
        @Query("action") action: String,
    ): ServiceActionResult

    @GET("v1/ops/logs/{host}/{name}")
    suspend fun logs(
        @Path("host") host: String,
        @Path("name") name: String,
        @Query("lines") lines: Int = 200,
    ): LogsResponse
}

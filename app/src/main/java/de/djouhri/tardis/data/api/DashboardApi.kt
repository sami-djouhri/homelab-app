package de.djouhri.tardis.data.api

import de.djouhri.tardis.data.model.tardis.DashboardSummary
import retrofit2.http.GET

interface DashboardApi {

    /** Nur `health` (Host-Metriken) + `inbox_counts` werden im Tardis genutzt. */
    @GET("v1/dashboard/summary")
    suspend fun summary(): DashboardSummary
}

package de.djouhri.cockpit.data.api

import de.djouhri.cockpit.data.model.cockpit.DashboardSummary
import retrofit2.http.GET

interface DashboardApi {

    /** Nur `health` (Host-Metriken) + `inbox_counts` werden im Cockpit genutzt. */
    @GET("v1/dashboard/summary")
    suspend fun summary(): DashboardSummary
}

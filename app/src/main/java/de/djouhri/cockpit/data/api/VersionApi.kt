package de.djouhri.cockpit.data.api

import de.djouhri.cockpit.data.model.cockpit.AppVersion
import retrofit2.http.GET

interface VersionApi {

    @GET("app-version")
    suspend fun appVersion(): AppVersion
}

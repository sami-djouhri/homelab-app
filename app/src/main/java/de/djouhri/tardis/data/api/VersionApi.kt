package de.djouhri.tardis.data.api

import de.djouhri.tardis.data.model.tardis.AppVersion
import retrofit2.http.GET

interface VersionApi {

    @GET("app-version")
    suspend fun appVersion(): AppVersion
}

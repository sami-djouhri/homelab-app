package de.djouhri.cockpit.data.api

import de.djouhri.cockpit.data.model.cockpit.HealthResponse
import de.djouhri.cockpit.data.model.cockpit.PairRequest
import de.djouhri.cockpit.data.model.cockpit.PairResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface PairingApi {

    /** Tauscht Nonce + CSR gegen Client-Cert + JWT. Kein Bearer noetig. */
    @POST("v1/auth/pair")
    suspend fun pair(@Body request: PairRequest): PairResponse

    /** Liveness-Probe (kein Auth) fuer den Verbindungstest in den Einstellungen. */
    @GET("v1/health")
    suspend fun health(): HealthResponse
}

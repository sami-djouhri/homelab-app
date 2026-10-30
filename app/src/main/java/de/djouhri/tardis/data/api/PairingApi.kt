package de.djouhri.tardis.data.api

import de.djouhri.tardis.data.model.tardis.GeraeteResponse
import de.djouhri.tardis.data.model.tardis.HealthResponse
import de.djouhri.tardis.data.model.tardis.PairRequest
import de.djouhri.tardis.data.model.tardis.PairResponse
import de.djouhri.tardis.data.model.tardis.WiderrufResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface PairingApi {

    /** Tauscht Nonce + CSR gegen Client-Cert + JWT. Kein Bearer noetig. */
    @POST("v1/auth/pair")
    suspend fun pair(@Body request: PairRequest): PairResponse

    /** Liveness-Probe (kein Auth) fuer den Verbindungstest in den Einstellungen. */
    @GET("v1/health")
    suspend fun health(): HealthResponse

    /**
     * Meldet DIESES Geraet beim Gateway ab und widerruft dabei seine Token.
     *
     * ⚠️ Braucht noch das gueltige Token (`require_device` am Gateway), also
     * **vor** dem lokalen Aufraeumen aufrufen. Wer erst lokal loescht, bekommt
     * hier 401 und widerruft nichts.
     */
    @POST("v1/auth/unpair")
    suspend fun unpair(): WiderrufResponse

    /**
     * Alle beim Gateway bekannten Geraete.
     *
     * Bewusst fuer jedes gekoppelte Geraet lesbar, nicht nur fuer ein
     * Verwalter-Konto: der Zweck ist, ein verlorenes Telefon von einem anderen
     * Geraet aus aussperren zu koennen.
     */
    @GET("v1/auth/geraete")
    suspend fun geraete(): GeraeteResponse

    /** Widerruft die Token eines ANDEREN Geraets (verlorenes Telefon). */
    @POST("v1/auth/geraete/{geraet_id}/widerrufen")
    suspend fun widerrufeGeraet(@Path("geraet_id") geraetId: String): WiderrufResponse
}

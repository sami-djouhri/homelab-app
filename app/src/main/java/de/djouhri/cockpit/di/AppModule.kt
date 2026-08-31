package de.djouhri.cockpit.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import de.djouhri.cockpit.BuildConfig
import de.djouhri.cockpit.security.MtlsProvider
import de.djouhri.cockpit.security.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideOkHttpClient(
        sessionState: SessionState,
        mtlsProvider: MtlsProvider,
    ): OkHttpClient {
        // Setzt Bearer-Token + aktuelle Gateway-Basis-URL. Liest den Zustand
        // NON-BLOCKING aus dem In-Memory-SessionState (kein runBlocking im Hot-Path).
        val authInterceptor = Interceptor { chain ->
            val token = sessionState.token
            val target = sessionState.gatewayBaseUrl().toHttpUrlOrNull()

            var request = chain.request()
            if (target != null) {
                val rewritten = request.url.newBuilder()
                    .scheme(target.scheme)
                    .host(target.host)
                    .port(target.port)
                    .build()
                request = request.newBuilder().url(rewritten).build()
            }
            if (!token.isNullOrBlank()) {
                request = request.newBuilder().addHeader("Authorization", "Bearer $token").build()
            }

            val response = chain.proceed(request)
            when {
                // Abgelaufenes/ungueltiges Token: NUR signalisieren, NICHT den
                // kompletten Pairing-Zustand (Keystore-Key/Cert) zerstoeren. Ein
                // transienter 401 soll kein Zwangs-Re-Pairing ausloesen.
                response.code == 401 && !token.isNullOrBlank() -> sessionState.reportUnauthorized()
                response.isSuccessful -> sessionState.clearUnauthorized()
            }
            response
        }

        // Kein BODY-Logging (leakte sonst Token/Payloads im Log).
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.NONE
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // mTLS: praesentiert das geraetegebundene Client-Cert und pinnt die
        // Homelab-CA, sobald das Gateway per TLS antwortet. Ueber Klartext-HTTP
        // (Phase 1) bleibt es inert. Schlaegt der Aufbau fehl, laeuft die App
        // ohne Client-Cert weiter (Server-Auth bleibt zustaendig).
        runCatching {
            val trustManager = mtlsProvider.trustManager()
            builder.sslSocketFactory(mtlsProvider.socketFactory(trustManager), trustManager)
        }

        return builder.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            // Platzhalter-Basis; die tatsaechliche Gateway-Adresse setzt der
            // Auth-Interceptor pro Request aus dem SessionState (QR-gateway_url).
            .baseUrl(normalizeBaseUrl(BuildConfig.GATEWAY_URL))
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    private fun normalizeBaseUrl(url: String): String =
        if (url.endsWith("/")) url else "$url/"
}

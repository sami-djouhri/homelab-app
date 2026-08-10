package de.djouhri.cockpit.data.model.cockpit

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Inhalt des Pairing-QR-Codes (von der internen Pairing-Seite erzeugt). */
@Serializable
data class QrPayload(
    @SerialName("gateway_url") val gatewayUrl: String = "",
    @SerialName("ca_fingerprint") val caFingerprint: String = "",
    val nonce: String,
)

@Serializable
data class PairRequest(
    val nonce: String,
    @SerialName("csr_pem") val csrPem: String,
    @SerialName("device_label") val deviceLabel: String? = null,
)

@Serializable
data class PairResponse(
    @SerialName("device_id") val deviceId: String,
    val jwt: String,
    @SerialName("client_cert_pem") val clientCertPem: String,
    @SerialName("ca_cert_pem") val caCertPem: String,
)

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
)

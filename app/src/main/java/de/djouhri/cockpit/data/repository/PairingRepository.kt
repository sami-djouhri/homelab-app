package de.djouhri.cockpit.data.repository

import de.djouhri.cockpit.data.api.PairingApi
import de.djouhri.cockpit.data.demo.DemoModeManager
import de.djouhri.cockpit.data.local.SettingsStore
import de.djouhri.cockpit.data.model.cockpit.PairRequest
import de.djouhri.cockpit.data.model.cockpit.QrPayload
import de.djouhri.cockpit.security.CertUtils
import de.djouhri.cockpit.security.DeviceKeystore
import de.djouhri.cockpit.security.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PairingRepository @Inject constructor(
    private val pairingApi: PairingApi,
    private val deviceKeystore: DeviceKeystore,
    private val settingsStore: SettingsStore,
    private val sessionState: SessionState,
    private val demo: DemoModeManager,
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /**
     * Vollstaendiger Pairing-Flow: QR-JSON parsen → das im QR genannte Gateway
     * ansteuern → Keypair im Keystore → CSR bauen → an das Gateway senden → die
     * zurueckgelieferte CA gegen den QR-Fingerprint pinnen → JWT + Zertifikate +
     * Gateway persistieren.
     */
    suspend fun pair(qrJson: String, deviceLabel: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = json.decodeFromString<QrPayload>(qrJson.trim())
            require(payload.nonce.isNotBlank()) { "QR ohne Nonce" }

            // Das Gateway aus dem QR gilt fuer diesen Pairing-Request (und danach).
            sessionState.overrideGateway(payload.gatewayUrl)

            deviceKeystore.generateKeyPair()
            val csr = deviceKeystore.buildCsrPem(deviceLabel)
            val response = pairingApi.pair(
                PairRequest(nonce = payload.nonce, csrPem = csr, deviceLabel = deviceLabel),
            )

            // Fingerprint-Pinning (fail-closed): der QR MUSS einen CA-Fingerprint
            // tragen und die vom Gateway gelieferte CA muss dazu passen. Sonst
            // koennte ein untergeschobener Pairing-Server eine Fremd-CA setzen.
            require(payload.caFingerprint.isNotBlank()) {
                "QR ohne CA-Fingerprint - Pinning erforderlich"
            }
            require(CertUtils.fingerprintMatches(payload.caFingerprint, response.caCertPem)) {
                "CA-Fingerprint stimmt nicht mit dem QR ueberein"
            }

            settingsStore.savePairing(
                jwt = response.jwt,
                deviceId = response.deviceId,
                clientCertPem = response.clientCertPem,
                caCertPem = response.caCertPem,
                caFingerprint = payload.caFingerprint,
                gatewayUrl = payload.gatewayUrl.ifBlank { null },
            )
            // Auth-Zustand sofort setzen (kein Post-Pairing-Token-Race).
            sessionState.applyPairing(
                gatewayUrl = payload.gatewayUrl.ifBlank { null },
                jwt = response.jwt,
                clientCert = response.clientCertPem,
                caCert = response.caCertPem,
                fingerprint = payload.caFingerprint,
            )
        }.onFailure {
            // Halb angelegten Schluessel + Gateway-Override bei Fehlschlag wieder entfernen.
            runCatching { deviceKeystore.deleteKey() }
            sessionState.overrideGateway(null)
        }
    }

    suspend fun unpair() = withContext(Dispatchers.IO) {
        demo.disable()
        settingsStore.unpair()
        sessionState.clearSession()
        runCatching { deviceKeystore.deleteKey() }
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        if (demo.isActive) return@withContext Result.success("demo")
        runCatching { pairingApi.health().status }
    }
}

package de.djouhri.tardis.data.repository

import de.djouhri.tardis.data.api.PairingApi
import de.djouhri.tardis.data.demo.DemoModeManager
import de.djouhri.tardis.data.local.SettingsStore
import de.djouhri.tardis.data.model.tardis.Geraet
import de.djouhri.tardis.data.model.tardis.PairRequest
import de.djouhri.tardis.data.model.tardis.QrPayload
import de.djouhri.tardis.data.model.tardis.WiderrufResponse
import de.djouhri.tardis.security.CertUtils
import de.djouhri.tardis.security.DeviceKeystore
import de.djouhri.tardis.security.SessionState
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
                "CA-Fingerprint stimmt nicht mit dem QR überein"
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

    /**
     * Entkoppelt das Geraet: ZUERST beim Gateway widerrufen, DANN lokal aufraeumen.
     *
     * Die Reihenfolge ist der Kern. `POST /v1/auth/unpair` laeuft am Gateway
     * durch `require_device` und braucht das noch gueltige Token. Wer erst lokal
     * loescht, bekommt dort eine 401 und widerruft nichts.
     *
     * ⚠️ Das lokale Aufraeumen laeuft deshalb trotzdem IMMER, auch wenn der
     * Widerruf scheitert. Ohne VPN oder bei totem Gateway muss man die App
     * loswerden koennen, sonst sitzt man in einer Kopplung fest.
     *
     * Rueckgabe, und sie gehoert in die Oberflaeche:
     * - `success(true)`  Widerruf durchgekommen, mindestens ein Token ungueltig.
     * - `success(false)` Gateway hat geantwortet, aber nichts zu widerrufen
     *   (war schon widerrufen, oder Demo-Modus ohne Kopplung). Kein Problem.
     * - `failure(e)`     Gateway NICHT erreicht. Lokal ist alles geloescht, das
     *   Token bleibt aber bis zu 30 Tage gueltig. Dieser Fall darf nicht
     *   verschluckt werden: ein stilles Scheitern erzeugt genau die Annahme,
     *   die ein Widerruf ausschliessen soll, naemlich das Geraet sei draussen.
     */
    suspend fun unpair(): Result<Boolean> = withContext(Dispatchers.IO) {
        entkoppelAblauf(
            widerrufen = { widerrufeDiesesGeraet() },
            lokalRaeumen = { entkoppleLokal() },
        )
    }

    /**
     * Schritt 1: nur der serverseitige Widerruf, ohne lokal etwas anzufassen.
     *
     * Muss VOR [entkoppleLokal] laufen (Token wird noch gebraucht). Getrennt
     * aufrufbar, damit die Oberflaeche zwischen den beiden Schritten fragen
     * kann: nach dem lokalen Loeschen kippt `isPaired` und die App wechselt
     * sofort auf den Pairing-Screen, eine Warnung waere dort nicht mehr zu
     * sehen. Genau das waere die stille Annahme, die ein Widerruf ausschliessen
     * soll.
     *
     * Im Demo-Modus `success(false)` ohne Netzzugriff: dort gibt es weder
     * Kopplung noch Token, und der Demo-Modus schliesst Requests aus.
     */
    suspend fun widerrufeDiesesGeraet(): Result<Boolean> = withContext(Dispatchers.IO) {
        if (demo.isActive) return@withContext Result.success(false)
        runCatching { pairingApi.unpair().widerrufen > 0 }
    }

    /**
     * Schritt 2: lokal aufraeumen. Danach kippt `isPaired` und Root wechselt.
     *
     * Laeuft bewusst auch dann, wenn [widerrufeDiesesGeraet] gescheitert ist:
     * ohne VPN oder bei totem Gateway muss man die App loswerden koennen, sonst
     * sitzt man in einer Kopplung fest, die sich nicht aufloesen laesst.
     */
    suspend fun entkoppleLokal() = withContext(Dispatchers.IO) {
        demo.disable()
        settingsStore.unpair()
        sessionState.clearSession()
        runCatching { deviceKeystore.deleteKey() }
        Unit
    }

    /**
     * Alle beim Gateway bekannten Geraete, neueste Kopplung zuerst.
     *
     * Im Demo-Modus eine leere Liste statt Beispieldaten: ohne Kopplung gibt es
     * keine Geraete, und erfundene waeren an dieser Stelle irrefuehrend.
     */
    suspend fun geraete(): Result<List<Geraet>> = withContext(Dispatchers.IO) {
        if (demo.isActive) return@withContext Result.success(emptyList())
        runCatching { pairingApi.geraete().geraete }
    }

    /**
     * Widerruft die Token eines ANDEREN Geraets (verlorenes Telefon).
     *
     * Liefert die Anzahl. **0 ist kein Fehler**, sondern „kenne ich nicht oder
     * war schon widerrufen" (siehe [WiderrufResponse]).
     */
    suspend fun widerrufeGeraet(geraetId: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching { pairingApi.widerrufeGeraet(geraetId).widerrufen }
    }

    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        if (demo.isActive) return@withContext Result.success("demo")
        runCatching { pairingApi.health().status }
    }
}

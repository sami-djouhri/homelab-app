package de.djouhri.cockpit.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import de.djouhri.cockpit.security.SecretCipher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Persistiert den Pairing-Zustand des Cockpits.
 *
 * Der private Geraeteschluessel liegt non-exportable im AndroidKeyStore (siehe
 * [de.djouhri.cockpit.security.DeviceKeystore]). Sensible Werte (Bearer-JWT,
 * Client-/CA-Zertifikat) werden vor dem Schreiben mit einem AndroidKeyStore-AES-
 * Schluessel verschluesselt (siehe [SecretCipher]) und liegen NICHT im Klartext im
 * DataStore. `device_id`, `ca_fingerprint` und `gateway_url` sind nicht geheim
 * (Hash-Praefix bzw. oeffentliche Werte) und bleiben Klartext. Backup ist per
 * Manifest ausgeschlossen, damit das Token nicht in die Cloud wandert.
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cipher: SecretCipher,
) {
    companion object {
        private val KEY_JWT_TOKEN = stringPreferencesKey("jwt_token_enc")
        private val KEY_DEVICE_ID = stringPreferencesKey("device_id")
        private val KEY_CLIENT_CERT = stringPreferencesKey("client_cert_pem_enc")
        private val KEY_CA_CERT = stringPreferencesKey("ca_cert_pem_enc")
        private val KEY_CA_FINGERPRINT = stringPreferencesKey("ca_fingerprint")
        private val KEY_GATEWAY_URL = stringPreferencesKey("gateway_url")
        private val KEY_REQUIRE_ACTION_CONFIRM = booleanPreferencesKey("require_action_confirm")
    }

    /** true, sobald ein (verschluesseltes) Token vorliegt - ohne es zu entschluesseln. */
    val isPaired: Flow<Boolean> = context.dataStore.data.map { !it[KEY_JWT_TOKEN].isNullOrBlank() }

    val jwtToken: Flow<String?> = context.dataStore.data.map { cipher.decryptOrNull(it[KEY_JWT_TOKEN]) }
    val deviceId: Flow<String?> = context.dataStore.data.map { it[KEY_DEVICE_ID] }
    val gatewayUrl: Flow<String?> = context.dataStore.data.map { it[KEY_GATEWAY_URL] }
    val caFingerprint: Flow<String?> = context.dataStore.data.map { it[KEY_CA_FINGERPRINT] }
    val clientCertPem: Flow<String?> = context.dataStore.data.map { cipher.decryptOrNull(it[KEY_CLIENT_CERT]) }
    val caCertPem: Flow<String?> = context.dataStore.data.map { cipher.decryptOrNull(it[KEY_CA_CERT]) }

    /** Ob schreibende Aktionen zusaetzlich lokal bestaetigt werden muessen (Default: ja). */
    val requireActionConfirm: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_REQUIRE_ACTION_CONFIRM] ?: true }

    suspend fun getJwtToken(): String? = cipher.decryptOrNull(context.dataStore.data.first()[KEY_JWT_TOKEN])

    suspend fun getDeviceId(): String? = context.dataStore.data.first()[KEY_DEVICE_ID]

    suspend fun getGatewayUrl(): String? = context.dataStore.data.first()[KEY_GATEWAY_URL]

    /** Speichert das Pairing-Ergebnis. Der private Schluessel bleibt im Keystore. */
    suspend fun savePairing(
        jwt: String,
        deviceId: String,
        clientCertPem: String,
        caCertPem: String,
        caFingerprint: String?,
        gatewayUrl: String?,
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_JWT_TOKEN] = cipher.encrypt(jwt)
            prefs[KEY_DEVICE_ID] = deviceId
            prefs[KEY_CLIENT_CERT] = cipher.encrypt(clientCertPem)
            prefs[KEY_CA_CERT] = cipher.encrypt(caCertPem)
            if (!caFingerprint.isNullOrBlank()) prefs[KEY_CA_FINGERPRINT] = caFingerprint
            if (!gatewayUrl.isNullOrBlank()) prefs[KEY_GATEWAY_URL] = gatewayUrl
        }
    }

    suspend fun setRequireActionConfirm(enabled: Boolean) {
        context.dataStore.edit { it[KEY_REQUIRE_ACTION_CONFIRM] = enabled }
    }

    /** Loescht saemtliche Auth-Artefakte aus dem DataStore (Keystore-Alias raeumt der Aufrufer). */
    suspend fun unpair() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_JWT_TOKEN)
            prefs.remove(KEY_DEVICE_ID)
            prefs.remove(KEY_CLIENT_CERT)
            prefs.remove(KEY_CA_CERT)
            prefs.remove(KEY_CA_FINGERPRINT)
            prefs.remove(KEY_GATEWAY_URL)
        }
    }
}

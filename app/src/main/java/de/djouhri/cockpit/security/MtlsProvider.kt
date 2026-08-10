package de.djouhri.cockpit.security

import java.net.Socket
import java.security.KeyStore
import java.security.Principal
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedKeyManager
import javax.net.ssl.X509TrustManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Baut das TLS-Material fuer den API-Verkehr:
 *
 *  - einen [X509ExtendedKeyManager], der das geraetegebundene Client-Zertifikat
 *    praesentiert und mit dem non-exportable AndroidKeyStore-Schluessel signiert
 *    (mTLS, Phase 2 - aktiv, sobald das Gateway per TLS mit `ssl_verify_client`
 *    antwortet);
 *  - einen [X509TrustManager], der Server-Zertifikate gegen die beim Pairing
 *    gepinnte Homelab-CA prueft und sonst auf die System-Trust-Anchor zurueckfaellt.
 *
 * Beide Manager lesen ihr Material dynamisch aus [SessionState]/[DeviceKeystore],
 * sodass der einmal gebaute OkHttp-Client nach dem Pairing nicht neu erstellt
 * werden muss. Bei Klartext-HTTP (Phase 1) wird nichts davon angefasst.
 *
 * Wichtig: Es gibt bewusst KEINEN „trust-all"-Pfad. Fehlt die CA, gilt die
 * System-Vertrauenskette - nie eine Abschaltung der Pruefung.
 */
@Singleton
class MtlsProvider @Inject constructor(
    private val sessionState: SessionState,
    private val deviceKeystore: DeviceKeystore,
) {
    fun trustManager(): X509TrustManager = DynamicTrustManager(sessionState)

    fun socketFactory(trustManager: X509TrustManager): SSLSocketFactory {
        val ctx = SSLContext.getInstance("TLS")
        ctx.init(
            arrayOf(DynamicKeyManager(sessionState, deviceKeystore)),
            arrayOf(trustManager),
            SecureRandom(),
        )
        return ctx.socketFactory
    }
}

/** Praesentiert das Client-Cert + den Keystore-Schluessel, sofern beides vorliegt. */
private class DynamicKeyManager(
    private val sessionState: SessionState,
    private val deviceKeystore: DeviceKeystore,
) : X509ExtendedKeyManager() {

    private companion object {
        const val ALIAS = "cockpit-mtls"
    }

    private fun chain(): Array<X509Certificate>? {
        val clientPem = sessionState.clientCertPem ?: return null
        val certs = runCatching { CertUtils.parsePemChain(clientPem) }.getOrNull().orEmpty().toMutableList()
        if (certs.isEmpty()) return null
        sessionState.caCertPem?.let { ca ->
            runCatching { CertUtils.parsePemChain(ca) }.getOrNull()?.let { certs.addAll(it) }
        }
        return certs.toTypedArray()
    }

    private fun available(): Boolean = deviceKeystore.getPrivateKey() != null && chain() != null

    override fun getClientAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? =
        if (available()) arrayOf(ALIAS) else null

    override fun chooseClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        socket: Socket?,
    ): String? = if (available()) ALIAS else null

    override fun chooseEngineClientAlias(
        keyType: Array<out String>?,
        issuers: Array<out Principal>?,
        engine: SSLEngine?,
    ): String? = if (available()) ALIAS else null

    override fun getCertificateChain(alias: String?): Array<X509Certificate>? =
        if (alias == ALIAS) chain() else null

    override fun getPrivateKey(alias: String?): PrivateKey? =
        if (alias == ALIAS) deviceKeystore.getPrivateKey() else null

    // Wir sind ausschliesslich Client - keine Server-Aliase.
    override fun getServerAliases(keyType: String?, issuers: Array<out Principal>?): Array<String>? = null
    override fun chooseServerAlias(keyType: String?, issuers: Array<out Principal>?, socket: Socket?): String? = null
    override fun chooseEngineServerAlias(keyType: String?, issuers: Array<out Principal>?, engine: SSLEngine?): String? = null
}

/**
 * Prueft Server-Zertifikate gegen die gepinnte Homelab-CA. Der Delegate wird
 * je CA-PEM einmal gebaut und gecacht. Ohne gepinnte CA gilt der System-Default
 * (kein Aufweichen der Pruefung).
 */
private class DynamicTrustManager(
    private val sessionState: SessionState,
) : X509TrustManager {

    private val systemDefault: X509TrustManager = buildTrustManager(null)

    @Volatile private var cachedPem: String? = null
    @Volatile private var cached: X509TrustManager? = null

    private fun active(): X509TrustManager {
        val pem = sessionState.caCertPem ?: return systemDefault
        cached?.let { if (pem == cachedPem) return it }
        val tm = runCatching { buildTrustManager(pem) }.getOrElse { return systemDefault }
        cachedPem = pem
        cached = tm
        return tm
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        active().checkClientTrusted(chain, authType)

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        active().checkServerTrusted(chain, authType)

    override fun getAcceptedIssuers(): Array<X509Certificate> = systemDefault.acceptedIssuers

    private companion object {
        fun buildTrustManager(caPem: String?): X509TrustManager {
            val keyStore: KeyStore? = if (caPem == null) {
                null
            } else {
                KeyStore.getInstance(KeyStore.getDefaultType()).apply {
                    load(null)
                    CertUtils.parsePemChain(caPem).forEachIndexed { i, cert ->
                        setCertificateEntry("homelab-ca-$i", cert)
                    }
                }
            }
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
            factory.init(keyStore)
            return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
        }
    }
}

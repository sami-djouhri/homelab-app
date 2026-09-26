package de.djouhri.cockpit.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.StringWriter
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verwaltet den geraetegebundenen Signaturschluessel im AndroidKeyStore
 * (hardware-backed, falls verfuegbar). Der private Schluessel verlaesst das
 * Geraet nie; fuer das Pairing wird nur ein PKCS#10-CSR erzeugt und signiert.
 *
 * Der signierte Client-Cert kommt spaeter (Phase-2-mTLS) zum Einsatz; in Phase 1
 * traegt allein das Bearer-JWT die Auth ueber das VPN.
 */
@Singleton
class DeviceKeystore @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "cockpit_device_key"
        private const val SIG_ALG = "SHA256withECDSA"
    }

    private fun loadStore(): KeyStore =
        KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    fun hasKey(): Boolean = loadStore().containsAlias(ALIAS)

    /**
     * Liefert den privaten Geraeteschluessel fuer das TLS-Client-Handshake
     * (mTLS). Der Schluessel ist non-exportable; die Signatur macht der Keystore.
     * `null`, wenn (noch) kein Schluessel existiert.
     */
    fun getPrivateKey(): PrivateKey? =
        runCatching { loadStore().getKey(ALIAS, null) as? PrivateKey }.getOrNull()

    /** Erzeugt ein frisches EC-P-256-Schluesselpaar im Keystore (idempotent: loescht alt). */
    fun generateKeyPair() {
        deleteKey()
        val generator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE,
        )
        val spec = KeyGenParameterSpec.Builder(
            ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY,
        )
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            .setDigests(KeyProperties.DIGEST_SHA256)
            .build()
        generator.initialize(spec)
        generator.generateKeyPair()
    }

    /** Baut einen PEM-CSR fuer den vorhandenen Keystore-Schluessel. */
    fun buildCsrPem(commonName: String): String {
        val store = loadStore()
        val privateKey = store.getKey(ALIAS, null) as? PrivateKey
            ?: error("Kein Geraeteschluessel im Keystore")
        val publicKey = store.getCertificate(ALIAS).publicKey

        val subject = X500Name("CN=${sanitizeCn(commonName)},OU=homelab-mobile")
        val csr = JcaPKCS10CertificationRequestBuilder(subject, publicKey)
            .build(KeystoreEcdsaSigner(privateKey))

        return StringWriter().use { writer ->
            JcaPEMWriter(writer).use { it.writeObject(csr) }
            writer.toString()
        }
    }

    fun deleteKey() {
        val store = loadStore()
        if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS)
    }

    private fun sanitizeCn(raw: String): String {
        val cleaned = raw.filter { it.isLetterOrDigit() || it == '-' || it == ' ' }.trim()
        return cleaned.take(48).ifBlank { "cockpit-device" }
    }

    /**
     * ContentSigner, der ueber den AndroidKeyStore-Schluessel signiert. BouncyCastle
     * uebernimmt nur die ASN.1-Kodierung; die Signatur selbst macht der Keystore.
     */
    private class KeystoreEcdsaSigner(private val privateKey: PrivateKey) : ContentSigner {
        private val buffer = ByteArrayOutputStream()
        private val algorithmId: AlgorithmIdentifier =
            DefaultSignatureAlgorithmIdentifierFinder().find(SIG_ALG)

        override fun getAlgorithmIdentifier(): AlgorithmIdentifier = algorithmId

        override fun getOutputStream(): OutputStream = buffer

        override fun getSignature(): ByteArray {
            val signature = Signature.getInstance(SIG_ALG)
            signature.initSign(privateKey)
            signature.update(buffer.toByteArray())
            return signature.sign()
        }
    }
}

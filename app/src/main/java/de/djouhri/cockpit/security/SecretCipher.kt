package de.djouhri.cockpit.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Verschluesselt sensible Werte (allen voran das Bearer-JWT), bevor sie im
 * DataStore landen. Der AES-256-Schluessel liegt non-exportable im
 * AndroidKeyStore (hardware-backed, falls verfuegbar) - das Klartext-Token
 * verlaesst also nie den sicheren Speicher.
 *
 * Format eines Ciphertext-Strings: `1:<base64(iv)>:<base64(ciphertext+tag)>`.
 * Das Praefix erlaubt spaetere Formatwechsel ohne Fehlinterpretation alter Werte.
 */
@Singleton
class SecretCipher @Inject constructor() {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "cockpit_secret_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
        private const val IV_BYTES = 12
        private const val PREFIX = "1"
    }

    private fun loadStore(): KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun secretKey(): SecretKey {
        val store = loadStore()
        (store.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    /** Verschluesselt einen Klartext-String zu einem selbstbeschreibenden Token. */
    fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "$PREFIX:${b64(iv)}:${b64(ciphertext)}"
    }

    /**
     * Entschluesselt einen zuvor mit [encrypt] erzeugten String. Gibt `null`
     * zurueck, wenn der Wert unlesbar ist (fremdes Format, rotierter/entfernter
     * Schluessel) - Aufrufer behandeln das wie „nicht vorhanden" und erzwingen
     * bei Bedarf ein erneutes Pairing.
     */
    fun decryptOrNull(stored: String?): String? {
        if (stored.isNullOrBlank()) return null
        return runCatching {
            val parts = stored.split(":", limit = 3)
            require(parts.size == 3 && parts[0] == PREFIX) { "unbekanntes Ciphertext-Format" }
            val iv = unb64(parts[1])
            require(iv.size == IV_BYTES) { "ungueltige IV-Laenge" }
            val ciphertext = unb64(parts[2])
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}

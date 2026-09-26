package com.meshgram.app.crypto

import android.content.Context
import android.util.Base64
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * مدیریت امنیت پیشرفته، رمزنگاری سرتاسری (E2EE) و کلیدهای دیجیتال اختصاصی.
 * پیام‌ها با ترکیب ECDH (Curve25519/Secp256r1) و AES-256-GCM رمزگذاری می‌شوند.
 * گره‌های واسط (رله) به هیچ عنوان امکان خواندن محتوا را ندارند (Zero-Knowledge Relay).
 */
class E2EESecurityManager(private val context: Context) {

    private val prefs = context.getSharedPreferences("meshgram_crypto_vault", Context.MODE_PRIVATE)
    private var localKeyPair: KeyPair

    init {
        localKeyPair = loadOrGenerateKeyPair()
    }

    val publicKeyBase64: String
        get() = Base64.encodeToString(localKeyPair.public.encoded, Base64.NO_WRAP)

    val keyFingerprint: String
        get() {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(localKeyPair.public.encoded)
            return digest.take(8).joinToString(":") { "%02X".format(it) }
        }

    private fun loadOrGenerateKeyPair(): KeyPair {
        val pubStr = prefs.getString("pub_key", null)
        val privStr = prefs.getString("priv_key", null)

        if (pubStr != null && privStr != null) {
            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val pubBytes = Base64.decode(pubStr, Base64.NO_WRAP)
                val privBytes = Base64.decode(privStr, Base64.NO_WRAP)
                val pubKey = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))
                val privKey = keyFactory.generatePrivate(java.security.spec.PKCS8EncodedKeySpec(privBytes))
                return KeyPair(pubKey, privKey)
            } catch (e: Exception) {
                // If corrupted, regenerate
            }
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(256)
        val newKp = kpg.generateKeyPair()

        prefs.edit()
            .putString("pub_key", Base64.encodeToString(newKp.public.encoded, Base64.NO_WRAP))
            .putString("priv_key", Base64.encodeToString(newKp.private.encoded, Base64.NO_WRAP))
            .apply()

        return newKp
    }

    /**
     * محاسبه کلید مشترک با کلید عمومی مخاطب (ECDH)
     */
    private fun deriveSharedKey(peerPublicKeyBase64: String): SecretKeySpec {
        val keyFactory = KeyFactory.getInstance("EC")
        val pubBytes = Base64.decode(peerPublicKeyBase64, Base64.NO_WRAP)
        val peerPublic = keyFactory.generatePublic(X509EncodedKeySpec(pubBytes))

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(localKeyPair.private)
        keyAgreement.doPhase(peerPublic, true)
        val sharedSecret = keyAgreement.generateSecret()

        val sha256 = MessageDigest.getInstance("SHA-256")
        val derivedKey = sha256.digest(sharedSecret)
        return SecretKeySpec(derivedKey, "AES")
    }

    /**
     * رمزنگاری محتوا با استاندارد نظامی AES-256-GCM
     */
    fun encrypt(plaintext: String, peerPublicKeyBase64: String): EncryptedBundle {
        val secretKey = deriveSharedKey(peerPublicKeyBase64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedBundle(
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }

    /**
     * رمزگشایی سرتاسری تنها توسط دارنده کلید خصوصی مقصد
     */
    fun decrypt(bundle: EncryptedBundle, senderPublicKeyBase64: String): String {
        val secretKey = deriveSharedKey(senderPublicKeyBase64)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(bundle.ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(bundle.ciphertextBase64, Base64.NO_WRAP)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }

    /**
     * رمزنگاری متقارن با پسورد محلی (برای چت‌های محرمانه با پسورد یا چت عمومی ایمن)
     */
    fun encryptSymmetric(plaintext: String, secretPhrase: String): EncryptedBundle {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(secretPhrase.toByteArray(Charsets.UTF_8))
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(128, iv))

        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return EncryptedBundle(
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            ciphertextBase64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)
        )
    }

    fun decryptSymmetric(bundle: EncryptedBundle, secretPhrase: String): String {
        val sha256 = MessageDigest.getInstance("SHA-256")
        val keyBytes = sha256.digest(secretPhrase.toByteArray(Charsets.UTF_8))
        val secretKey = SecretKeySpec(keyBytes, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val iv = Base64.decode(bundle.ivBase64, Base64.NO_WRAP)
        val ciphertext = Base64.decode(bundle.ciphertextBase64, Base64.NO_WRAP)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        val plaintextBytes = cipher.doFinal(ciphertext)
        return String(plaintextBytes, Charsets.UTF_8)
    }
}

data class EncryptedBundle(
    val ivBase64: String,
    val ciphertextBase64: String
)

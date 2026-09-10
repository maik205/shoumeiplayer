package com.maik205.shoumeiplayer.remote

import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.abs

/**
 * Ephemeral Elliptic-Curve Diffie-Hellman (ECDH) on curve P-256 (secp256r1)
 * paired with AES-256-GCM authenticated encryption for secure transfer of
 * sensitive session tokens over local Wi-Fi.
 */
object RemoteCrypto {
    private const val EC_CURVE = "secp256r1"
    private const val AES_GCM = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH_BITS = 128
    private const val GCM_IV_LENGTH_BYTES = 12

    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(ECGenParameterSpec(EC_CURVE))
        return generator.generateKeyPair()
    }

    fun decodePublicKey(encoded: ByteArray): PublicKey {
        val keySpec = X509EncodedKeySpec(encoded)
        return KeyFactory.getInstance("EC").generatePublic(keySpec)
    }

    fun deriveSharedSecret(keyPair: KeyPair, peerPublicKey: PublicKey): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(keyPair.private)
        agreement.doPhase(peerPublicKey, true)
        return agreement.generateSecret()
    }

    /**
     * Derives an AES-256 key and a 4-digit Short Authentication String (SAS / PIN)
     * from the shared secret and salt.
     */
    fun deriveKeysAndPin(sharedSecret: ByteArray, salt: ByteArray = ByteArray(0)): DerivedSessionKeys {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(salt)
        val hash = digest.digest(sharedSecret)

        // Derive 4-digit verification PIN from the first 4 bytes
        val pinVal = (
            ((hash[0].toInt() and 0xFF) shl 24) or
            ((hash[1].toInt() and 0xFF) shl 16) or
            ((hash[2].toInt() and 0xFF) shl 8) or
            (hash[3].toInt() and 0xFF)
        )
        val pin = (abs(pinVal) % 10000).toString().padStart(4, '0')

        return DerivedSessionKeys(
            aesKey = hash,
            pin = pin,
        )
    }

    fun encrypt(plainText: ByteArray, aesKey: ByteArray): EncryptedPayload {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(AES_GCM)
        val keySpec = SecretKeySpec(aesKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec)
        val cipherText = cipher.doFinal(plainText)
        return EncryptedPayload(iv = iv, cipherText = cipherText)
    }

    fun decrypt(cipherText: ByteArray, iv: ByteArray, aesKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        val keySpec = SecretKeySpec(aesKey, "AES")
        val gcmSpec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec)
        return cipher.doFinal(cipherText)
    }
}

data class DerivedSessionKeys(
    val aesKey: ByteArray,
    val pin: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as DerivedSessionKeys
        return aesKey.contentEquals(other.aesKey) && pin == other.pin
    }

    override fun hashCode(): Int = 31 * aesKey.contentHashCode() + pin.hashCode()
}

data class EncryptedPayload(
    val iv: ByteArray,
    val cipherText: ByteArray,
) {
    fun ivBase64(): String = Base64.getEncoder().encodeToString(iv)
    fun cipherTextBase64(): String = Base64.getEncoder().encodeToString(cipherText)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EncryptedPayload
        return iv.contentEquals(other.iv) && cipherText.contentEquals(other.cipherText)
    }

    override fun hashCode(): Int = 31 * iv.contentHashCode() + cipherText.contentHashCode()
}

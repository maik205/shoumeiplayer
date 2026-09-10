package com.maik205.shoumeiplayer.remote

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.util.Base64
import javax.crypto.AEADBadTagException

class RemoteCryptoTest {

    @Test
    fun ecdhKeyAgreement_derivesMatchingSharedSecretAndPin() {
        // Client generates ephemeral keypair
        val clientKeyPair = RemoteCrypto.generateKeyPair()
        val clientPublicBytes = clientKeyPair.public.encoded

        // Server generates ephemeral keypair
        val serverKeyPair = RemoteCrypto.generateKeyPair()
        val serverPublicBytes = serverKeyPair.public.encoded

        // Server decodes client's public key and derives shared secret
        val decodedClientPublic = RemoteCrypto.decodePublicKey(clientPublicBytes)
        val serverSharedSecret = RemoteCrypto.deriveSharedSecret(serverKeyPair, decodedClientPublic)

        // Client decodes server's public key and derives shared secret
        val decodedServerPublic = RemoteCrypto.decodePublicKey(serverPublicBytes)
        val clientSharedSecret = RemoteCrypto.deriveSharedSecret(clientKeyPair, decodedServerPublic)

        // Both shared secrets must match byte-for-byte
        assertArrayEquals(serverSharedSecret, clientSharedSecret)

        // Derive session keys and 4-digit SAS / PIN
        val salt = "shoumei-salt".toByteArray(Charsets.UTF_8)
        val serverSession = RemoteCrypto.deriveKeysAndPin(serverSharedSecret, salt)
        val clientSession = RemoteCrypto.deriveKeysAndPin(clientSharedSecret, salt)

        assertArrayEquals(serverSession.aesKey, clientSession.aesKey)
        assertEquals(serverSession.pin, clientSession.pin)
        assertEquals(4, serverSession.pin.length)
    }

    @Test
    fun aesGcm_encryptAndDecryptRoundtripSucceeds() {
        val clientKeyPair = RemoteCrypto.generateKeyPair()
        val serverKeyPair = RemoteCrypto.generateKeyPair()

        val sharedSecret = RemoteCrypto.deriveSharedSecret(serverKeyPair, clientKeyPair.public)
        val sessionKeys = RemoteCrypto.deriveKeysAndPin(sharedSecret)

        val sensitivePlaintext = """{"token":"jellyfin-secret-abc-123","url":"http://192.168.1.50:8096"}"""
            .toByteArray(Charsets.UTF_8)

        // TV encrypts sensitive credentials
        val encrypted = RemoteCrypto.encrypt(sensitivePlaintext, sessionKeys.aesKey)

        // Companion decrypts
        val decrypted = RemoteCrypto.decrypt(encrypted.cipherText, encrypted.iv, sessionKeys.aesKey)

        assertEquals(
            """{"token":"jellyfin-secret-abc-123","url":"http://192.168.1.50:8096"}""",
            String(decrypted, Charsets.UTF_8)
        )
    }

    @Test
    fun aesGcm_tamperedCiphertextFailsAuthentication() {
        val keyPair1 = RemoteCrypto.generateKeyPair()
        val keyPair2 = RemoteCrypto.generateKeyPair()
        val sharedSecret = RemoteCrypto.deriveSharedSecret(keyPair1, keyPair2.public)
        val sessionKeys = RemoteCrypto.deriveKeysAndPin(sharedSecret)

        val plaintext = "test message".toByteArray(Charsets.UTF_8)
        val encrypted = RemoteCrypto.encrypt(plaintext, sessionKeys.aesKey)

        // Tamper with one byte in the ciphertext
        val tampered = encrypted.cipherText.clone()
        tampered[0] = (tampered[0].toInt() xor 0xFF).toByte()

        assertThrows(AEADBadTagException::class.java) {
            RemoteCrypto.decrypt(tampered, encrypted.iv, sessionKeys.aesKey)
        }
    }
}

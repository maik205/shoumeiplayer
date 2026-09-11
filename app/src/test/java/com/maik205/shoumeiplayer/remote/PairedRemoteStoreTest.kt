package com.maik205.shoumeiplayer.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PairedRemoteStoreTest {

    @Test
    fun createPairing_setsSevenDaysExpiration() {
        val store = PairedRemoteStore(prefs = null)
        val now = 1_000_000_000L
        val client = store.createPairing("client-1", "Living Room Phone", nowMs = now)

        assertEquals("client-1", client.id)
        assertEquals("Living Room Phone", client.deviceName)
        assertTrue(client.pairingToken.isNotBlank())
        assertEquals(now, client.pairedAtMs)
        assertEquals(now + PAIRING_EXPIRY_MS, client.expiresAtMs)
        assertEquals(7L * 24 * 60 * 60 * 1000L, client.expiresAtMs - client.pairedAtMs)
    }

    @Test
    fun findValidClient_returnsClientWhenWithinSevenDays() {
        val store = PairedRemoteStore(prefs = null)
        val pairedAt = 1_000_000_000L
        val client = store.createPairing("client-1", "Living Room Phone", nowMs = pairedAt)

        // 6 days later: still valid
        val sixDaysLater = pairedAt + (6L * 24 * 60 * 60 * 1000L)
        val retrieved = store.findValidClient(client.pairingToken, nowMs = sixDaysLater)
        assertNotNull(retrieved)
        assertEquals(client.pairingToken, retrieved?.pairingToken)
    }

    @Test
    fun findValidClient_returnsNullAndEvictsWhenExpiredAfterSevenDays() {
        val store = PairedRemoteStore(prefs = null)
        val pairedAt = 1_000_000_000L
        val client = store.createPairing("client-1", "Living Room Phone", nowMs = pairedAt)

        // Exactly 7 days later: expired
        val sevenDaysLater = pairedAt + PAIRING_EXPIRY_MS
        val retrievedAtExpiry = store.findValidClient(client.pairingToken, nowMs = sevenDaysLater)
        assertNull("Client must be null at or past 7 days expiration", retrievedAtExpiry)

        // Store should have evicted the expired pairing
        assertNull(store.findClient(client.pairingToken))
    }

    @Test
    fun purgeExpired_removesOnlyExpiredClients() {
        val store = PairedRemoteStore(prefs = null)
        val now = 1_000_000_000L
        val client1 = store.createPairing("client-1", "Phone 1", nowMs = now)
        val client2 = store.createPairing("client-2", "Phone 2", nowMs = now + (3L * 24 * 60 * 60 * 1000L))

        // Check at now + 8 days (client1 expired, client2 still has 2 days)
        val eightDaysLater = now + (8L * 24 * 60 * 60 * 1000L)
        store.purgeExpired(nowMs = eightDaysLater)

        assertNull(store.findClient(client1.pairingToken))
        assertNotNull(store.findClient(client2.pairingToken))
    }

    @Test
    fun removeClient_revokesPairingImmediately() {
        val store = PairedRemoteStore(prefs = null)
        val client = store.createPairing("client-1", "Phone", nowMs = 1000L)
        assertNotNull(store.findClient(client.pairingToken))

        store.removeClient(client.pairingToken)
        assertNull(store.findClient(client.pairingToken))
    }
}

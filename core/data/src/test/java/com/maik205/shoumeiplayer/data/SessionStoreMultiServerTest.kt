package com.maik205.shoumeiplayer.data

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionStoreMultiServerTest {

    @Test
    fun `switching servers restores each persisted token`() = runTest {
        val store = FakeJellyfin.newSessionStore()

        store.activateServer("http://living-room:8096", "server-a", "Living room")
        store.saveAuth("token-a", "user-a", "Alice")
        store.activateServer("https://remote.example.com", "server-b", "Remote")
        store.saveAuth("token-b", "user-b", "Bob")

        store.activateServer("http://living-room:8096", "server-a", "Living room")
        assertEquals("token-a", store.current()?.accessToken)
        assertEquals("Alice", store.current()?.userName)

        store.activateServer("https://remote.example.com", "server-b", "Remote")
        assertEquals("token-b", store.current()?.accessToken)
        assertEquals("Bob", store.current()?.userName)
        assertEquals(2, store.rememberedServers.first().size)
    }

    @Test
    fun `same server id keeps its session when its address changes`() = runTest {
        val store = FakeJellyfin.newSessionStore()
        store.activateServer("http://192.168.1.14:8096", "server-a", "Living room")
        store.saveAuth("token-a", "user-a", "Alice")

        store.activateServer("https://jellyfin.example.com", "server-a", "Living room")

        assertEquals("https://jellyfin.example.com", store.serverUrlOrNull())
        assertEquals("token-a", store.current()?.accessToken)
        assertEquals(1, store.rememberedServers.first().size)
    }

    @Test
    fun `sign out and forget are scoped to the active server`() = runTest {
        val store = FakeJellyfin.newSessionStore()
        store.activateServer("http://one", "one", "One")
        store.saveAuth("token-one", "user-one", "One user")
        store.activateServer("http://two", "two", "Two")
        store.saveAuth("token-two", "user-two", "Two user")

        store.clearAuth()
        assertNull(store.current())
        assertFalse(store.rememberedServers.first().single { it.id == "two" }.hasSession)
        assertTrue(store.rememberedServers.first().single { it.id == "one" }.hasSession)

        store.clearServer()
        assertNull(store.serverUrlOrNull())
        assertEquals(listOf("one"), store.rememberedServers.first().map { it.id })

        store.activateServer("http://one", "one", "One")
        assertEquals("token-one", store.current()?.accessToken)
    }
}

package com.maik205.shoumeiplayer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.maik205.shoumeiplayer.data.api.JellyfinClient
import com.maik205.shoumeiplayer.data.session.Session
import com.maik205.shoumeiplayer.data.session.SessionProvider
import com.maik205.shoumeiplayer.data.session.SessionStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory [DataStore] of [Preferences] for JVM unit tests.
 *
 * The file-backed DataStore cannot be used here: its write path renames
 * `<file>.tmp` over `<file>`, and `File.renameTo` fails on Windows once the
 * destination exists, so the *second* write of any test always threw
 * `IOException: Unable to rename ...`.
 */
class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
        mutex.withLock {
            val updated = transform(state.value)
            state.value = updated
            updated
        }
}

/** A single mocked HTTP response for a request path. */
data class FakeRoute(val status: HttpStatusCode, val body: String)

/** Shorthand for a 200 OK route. */
fun fakeRoute(body: String, status: HttpStatusCode = HttpStatusCode.OK): FakeRoute = FakeRoute(status, body)

/** [SessionProvider] test double with a fixed (possibly null) session. */
class StaticSessionProvider(
    private val session: Session?,
    private val deviceId: String = "test-device-id",
) : SessionProvider {
    override suspend fun current(): Session? = session
    override suspend fun deviceId(): String = deviceId
    override suspend fun serverUrlOrNull(): String? = session?.serverUrl
}

/**
 * Captures every request the mock engine sees, so tests can assert on the *query* a repository
 * builds (`fields=`, `enableUserData=`, …) and not only on the response it unwraps.
 */
class RequestRecorder {
    private val recorded = mutableListOf<HttpRequestData>()

    fun record(request: HttpRequestData) {
        recorded += request
    }

    /** How many requests were seen — lets a test assert that a call was *not* made. */
    fun count(): Int = recorded.size

    /** Every recorded request path, in order. */
    fun paths(): List<String> = recorded.map { it.url.encodedPath }

    /** First value of [name] on the request at [index]. */
    fun queryAt(index: Int, name: String): String? = recorded[index].url.parameters[name]

    /** The HTTP method of the request at [index]. */
    fun methodAt(index: Int): String = recorded[index].method.value

    /** Authorization header sent with the request at [index]. */
    fun authorizationAt(index: Int): String? = recorded[index].headers[HttpHeaders.Authorization]

    /**
     * The serialized request body at [index], or "" when the request carried none. Ktor's
     * ContentNegotiation has already rendered `@Serializable` bodies to JSON by the time the engine
     * sees them, so this is what actually goes on the wire.
     */
    fun bodyAt(index: Int): String = (recorded[index].body as? TextContent)?.text.orEmpty()

    /** Body of the only recorded request. */
    fun body(): String = (single().body as? TextContent)?.text.orEmpty()

    /** The only recorded request; fails loudly when a test recorded zero or several. */
    fun single(): HttpRequestData {
        check(recorded.size == 1) { "expected exactly 1 recorded request, got ${recorded.size}" }
        return recorded.first()
    }

    fun path(): String = single().url.encodedPath

    /** First value of [name] on the only recorded request, or null when absent. */
    fun query(name: String): String? = single().url.parameters[name]
}

/**
 * [MockEngine] pinned to [Dispatchers.Unconfined].
 *
 * The stock engine runs every call on Ktor's own thread pool, so a request issued from a
 * `viewModelScope` coroutine hops off the test scheduler and its continuation lands back on
 * `Dispatchers.Main` only *after* `advanceUntilIdle()` has already decided the scheduler is idle.
 * That made every ViewModel test that asserts on post-request state racy (a write would still read
 * `saving = true`). Unconfined keeps the whole round trip on the calling coroutine, so
 * `advanceUntilIdle()` observes it deterministically.
 */
private class UnconfinedMockEngine(config: MockEngineConfig) : MockEngine(config) {
    override val dispatcher: CoroutineDispatcher = Dispatchers.Unconfined
}

object FakeJellyfin {

    /** A real [SessionStore] over a fresh in-memory DataStore, safe for JVM unit tests. */
    fun newSessionStore(): SessionStore = SessionStore(InMemoryPreferencesDataStore())

    /**
     * A [JellyfinClient] wired to a Ktor [MockEngine] that resolves requests purely
     * by [io.ktor.http.Url.encodedPath] against [routes] (query string and host are
     * ignored, since tests only care what body/status a given endpoint returns).
     */
    fun client(
        routes: Map<String, FakeRoute>,
        sessions: SessionProvider,
        appName: String = "Shoumei Player Test",
        appVersion: String = "1.0-test",
        recorder: RequestRecorder? = null,
    ): JellyfinClient {
        val config = MockEngineConfig().apply {
            addHandler { request: HttpRequestData ->
                recorder?.record(request)
                val path = request.url.encodedPath
                val route = routes[path]
                if (route == null) {
                    respondError(HttpStatusCode.NotFound, "No fake route registered for $path")
                } else {
                    respond(
                        content = route.body,
                        status = route.status,
                        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
                    )
                }
            }
        }
        return JellyfinClient(sessions, appName, appVersion, UnconfinedMockEngine(config))
    }

    /** Reads a JSON fixture from `src/test/resources/fixtures/`. */
    fun fixture(name: String): String {
        val stream = requireNotNull(FakeJellyfin::class.java.classLoader?.getResourceAsStream("fixtures/$name")) {
            "fixture $name not found on test classpath"
        }
        return stream.bufferedReader().use { it.readText() }
    }
}

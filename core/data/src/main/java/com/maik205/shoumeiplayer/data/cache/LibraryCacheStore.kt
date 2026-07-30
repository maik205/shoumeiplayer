package com.maik205.shoumeiplayer.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.maik205.shoumeiplayer.domain.model.LibraryDestination
import com.maik205.shoumeiplayer.domain.model.MediaItem
import com.maik205.shoumeiplayer.domain.model.MediaShelf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Small disk snapshot used to render the library rail before the first network response.
 *
 * The snapshot is keyed by server and user so switching profiles cannot briefly expose another
 * account's libraries. It is only a bootstrap hint; callers must always refresh it from Jellyfin.
 */
class LibraryCacheStore(private val store: DataStore<Preferences>) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val generation = AtomicLong()
    private val latestGeneration = ConcurrentHashMap<String, Long>()
    private val lastEncoded = ConcurrentHashMap<String, String>()
    private val writeMutex = Mutex()

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("shoumei_library_cache")
        },
    )

    suspend fun read(serverUrl: String, userId: String): List<LibraryDestination> {
        return withContext(Dispatchers.IO) {
            val persisted = store.data.first()[Keys.SNAPSHOT]
                ?.let { encoded -> runCatching { json.decodeFromString<PersistedLibrarySnapshot>(encoded) }.getOrNull() }
                ?: return@withContext emptyList()
            if (persisted.serverUrl != serverUrl || persisted.userId != userId) return@withContext emptyList()
            persisted.libraries.map { it.toUi() }
        }
    }

    suspend fun write(serverUrl: String, userId: String, libraries: List<LibraryDestination>) {
        val key = "snapshot:$serverUrl:$userId"
        val writeGeneration = generation.incrementAndGet().also { latestGeneration[key] = it }
        val encoded = withContext(Dispatchers.IO) {
            json.encodeToString(
                PersistedLibrarySnapshot(
                    serverUrl = serverUrl,
                    userId = userId,
                    libraries = libraries.map(LibraryDestination::toPersisted),
                ),
            )
        }
        writeEncoded(key, writeGeneration, Keys.SNAPSHOT, encoded)
    }

    suspend fun clear() {
        withContext(Dispatchers.IO) {
            store.edit { it.remove(Keys.SNAPSHOT) }
            latestGeneration.clear()
            lastEncoded.clear()
        }
    }

    suspend fun readHome(serverUrl: String, userId: String): HomeCache? {
        return withContext(Dispatchers.IO) {
            val persisted = store.data.first()[Keys.HOME]
                ?.let { encoded -> runCatching { json.decodeFromString<PersistedHomeSnapshot>(encoded) }.getOrNull() }
                ?: return@withContext null
            if (persisted.serverUrl != serverUrl || persisted.userId != userId) return@withContext null
            HomeCache(persisted.libraries.map { it.toUi() }, persisted.shelves, persisted.heroItemId)
        }
    }

    suspend fun writeHome(
        serverUrl: String,
        userId: String,
        libraries: List<LibraryDestination>,
        shelves: List<MediaShelf>,
        heroItemId: String?,
    ) {
        val key = "home:$serverUrl:$userId"
        val writeGeneration = generation.incrementAndGet().also { latestGeneration[key] = it }
        val encoded = withContext(Dispatchers.IO) {
            json.encodeToString(
                PersistedHomeSnapshot(
                    serverUrl = serverUrl,
                    userId = userId,
                    libraries = libraries.map(LibraryDestination::toPersisted),
                    shelves = shelves,
                    heroItemId = heroItemId,
                ),
            )
        }
        writeEncoded(key, writeGeneration, Keys.HOME, encoded)
    }

    suspend fun readLibrary(
        serverUrl: String,
        userId: String,
        libraryId: String,
        sort: String,
        view: String,
    ): LibraryPageCache? {
        return withContext(Dispatchers.IO) {
            val snapshot = store.data.first()[libraryKey(libraryId, sort, view)]
                ?.let { encoded -> runCatching { json.decodeFromString<PersistedLibraryPage>(encoded) }.getOrNull() }
                ?: return@withContext null
            if (snapshot.serverUrl != serverUrl || snapshot.userId != userId) return@withContext null
            LibraryPageCache(snapshot.items, snapshot.totalCount, snapshot.exhausted)
        }
    }

    suspend fun writeLibrary(
        serverUrl: String,
        userId: String,
        libraryId: String,
        sort: String,
        view: String,
        items: List<MediaItem>,
        totalCount: Int,
        exhausted: Boolean,
    ) {
        val preferenceKey = libraryKey(libraryId, sort, view)
        val key = "library:$serverUrl:$userId:$libraryId:$sort:$view"
        val writeGeneration = generation.incrementAndGet().also { latestGeneration[key] = it }
        val encoded = withContext(Dispatchers.IO) {
            json.encodeToString(
                PersistedLibraryPage(
                    serverUrl = serverUrl,
                    userId = userId,
                    items = items,
                    totalCount = totalCount,
                    exhausted = exhausted,
                ),
            )
        }
        writeEncoded(key, writeGeneration, preferenceKey, encoded)
    }

    private suspend fun writeEncoded(
        key: String,
        writeGeneration: Long,
        preferenceKey: androidx.datastore.preferences.core.Preferences.Key<String>,
        encoded: String,
    ) = withContext(Dispatchers.IO) {
        writeMutex.withLock {
            if (latestGeneration[key] != writeGeneration) return@withLock
            if (lastEncoded[key] == encoded) return@withLock
            store.edit { preferences -> preferences[preferenceKey] = encoded }
            lastEncoded[key] = encoded
        }
    }

    private fun libraryKey(libraryId: String, sort: String, view: String) =
        stringPreferencesKey("library_page_v2_${libraryId}_${sort}_$view")

    private object Keys {
        val SNAPSHOT = stringPreferencesKey("library_snapshot_v1")
        val HOME = stringPreferencesKey("home_snapshot_v1")
    }
}

data class HomeCache(
    val libraries: List<LibraryDestination>,
    val shelves: List<MediaShelf>,
    val heroItemId: String?,
)

data class LibraryPageCache(
    val items: List<MediaItem>,
    val totalCount: Int,
    val exhausted: Boolean,
)

@Serializable
private data class PersistedLibrarySnapshot(
    val serverUrl: String,
    val userId: String,
    val libraries: List<PersistedLibrary> = emptyList(),
)

@Serializable
private data class PersistedLibrary(
    val id: String,
    val title: String,
    val collectionType: String? = null,
) {
    fun toUi() = LibraryDestination(id = id, title = title, collectionType = collectionType)
}

@Serializable
private data class PersistedHomeSnapshot(
    val serverUrl: String,
    val userId: String,
    val libraries: List<PersistedLibrary> = emptyList(),
    val shelves: List<MediaShelf> = emptyList(),
    val heroItemId: String? = null,
)

@Serializable
private data class PersistedLibraryPage(
    val serverUrl: String,
    val userId: String,
    val items: List<MediaItem> = emptyList(),
    val totalCount: Int = 0,
    val exhausted: Boolean = false,
)

private fun LibraryDestination.toPersisted() = PersistedLibrary(
    id = id,
    title = title,
    collectionType = collectionType,
)

package com.maik205.shoumeiplayer.data.cache

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.maik205.shoumeiplayer.ui.television.model.LibraryDestinationUi
import com.maik205.shoumeiplayer.ui.television.model.MediaShelfUi
import com.maik205.shoumeiplayer.ui.television.model.MediaItemUi
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

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

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("shoumei_library_cache")
        },
    )

    suspend fun read(serverUrl: String, userId: String): List<LibraryDestinationUi> {
        val persisted = store.data.first()[Keys.SNAPSHOT]
            ?.let { encoded -> runCatching { json.decodeFromString<PersistedLibrarySnapshot>(encoded) }.getOrNull() }
            ?: return emptyList()
        if (persisted.serverUrl != serverUrl || persisted.userId != userId) return emptyList()
        return persisted.libraries.map { it.toUi() }
    }

    suspend fun write(serverUrl: String, userId: String, libraries: List<LibraryDestinationUi>) {
        store.edit { preferences ->
            preferences[Keys.SNAPSHOT] = json.encodeToString(
                PersistedLibrarySnapshot(
                    serverUrl = serverUrl,
                    userId = userId,
                    libraries = libraries.map(LibraryDestinationUi::toPersisted),
                ),
            )
        }
    }

    suspend fun clear() {
        store.edit { it.remove(Keys.SNAPSHOT) }
    }

    suspend fun readHome(serverUrl: String, userId: String): HomeCache? {
        val persisted = store.data.first()[Keys.HOME]
            ?.let { encoded -> runCatching { json.decodeFromString<PersistedHomeSnapshot>(encoded) }.getOrNull() }
            ?: return null
        if (persisted.serverUrl != serverUrl || persisted.userId != userId) return null
        return HomeCache(persisted.libraries.map { it.toUi() }, persisted.shelves, persisted.heroItemId)
    }

    suspend fun writeHome(
        serverUrl: String,
        userId: String,
        libraries: List<LibraryDestinationUi>,
        shelves: List<MediaShelfUi>,
        heroItemId: String?,
    ) {
        store.edit { preferences ->
            preferences[Keys.HOME] = json.encodeToString(
                PersistedHomeSnapshot(
                    serverUrl = serverUrl,
                    userId = userId,
                    libraries = libraries.map(LibraryDestinationUi::toPersisted),
                    shelves = shelves,
                    heroItemId = heroItemId,
                ),
            )
        }
    }

    suspend fun readLibrary(
        serverUrl: String,
        userId: String,
        libraryId: String,
        sort: String,
        view: String,
    ): LibraryPageCache? {
        val snapshot = store.data.first()[libraryKey(libraryId, sort, view)]
            ?.let { encoded -> runCatching { json.decodeFromString<PersistedLibraryPage>(encoded) }.getOrNull() }
            ?: return null
        if (snapshot.serverUrl != serverUrl || snapshot.userId != userId) return null
        return LibraryPageCache(snapshot.items, snapshot.totalCount, snapshot.exhausted)
    }

    suspend fun writeLibrary(
        serverUrl: String,
        userId: String,
        libraryId: String,
        sort: String,
        view: String,
        items: List<MediaItemUi>,
        totalCount: Int,
        exhausted: Boolean,
    ) {
        store.edit { preferences ->
            preferences[libraryKey(libraryId, sort, view)] = json.encodeToString(
                PersistedLibraryPage(
                    serverUrl = serverUrl,
                    userId = userId,
                    items = items,
                    totalCount = totalCount,
                    exhausted = exhausted,
                ),
            )
        }
    }

    private fun libraryKey(libraryId: String, sort: String, view: String) =
        stringPreferencesKey("library_page_v1_${libraryId}_${sort}_$view")

    private object Keys {
        val SNAPSHOT = stringPreferencesKey("library_snapshot_v1")
        val HOME = stringPreferencesKey("home_snapshot_v1")
    }
}

data class HomeCache(
    val libraries: List<LibraryDestinationUi>,
    val shelves: List<MediaShelfUi>,
    val heroItemId: String?,
)

data class LibraryPageCache(
    val items: List<MediaItemUi>,
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
    fun toUi() = LibraryDestinationUi(id = id, title = title, collectionType = collectionType)
}

@Serializable
private data class PersistedHomeSnapshot(
    val serverUrl: String,
    val userId: String,
    val libraries: List<PersistedLibrary> = emptyList(),
    val shelves: List<MediaShelfUi> = emptyList(),
    val heroItemId: String? = null,
)

@Serializable
private data class PersistedLibraryPage(
    val serverUrl: String,
    val userId: String,
    val items: List<MediaItemUi> = emptyList(),
    val totalCount: Int = 0,
    val exhausted: Boolean = false,
)

private fun LibraryDestinationUi.toPersisted() = PersistedLibrary(
    id = id,
    title = title,
    collectionType = collectionType,
)

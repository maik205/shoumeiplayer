package com.maik205.shoumeiplayer.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.maik205.shoumeiplayer.domain.settings.PreferredQuality
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a stored preference is attached to.
 *
 * The id is part of the storage key, so it must stay stable: renaming one orphans every row it
 * owns. [Account] is the degenerate case — one row per user — and uses [ACCOUNT_ID] as its entity.
 */
enum class PreferenceEntity(val storageId: String) {
    Account("account"),
    Library("library"),
    Series("series"),
    Item("item"),
    ;

    companion object {
        /** The single entity id every [Account]-scoped row uses. */
        const val ACCOUNT_ID: String = "self"
    }
}

/** How a library was last presented, so reopening it looks the way the viewer left it. */
data class LibraryPresentation(val sort: String, val view: String)

/** Per-item lip-sync and subtitle-timing correction, in milliseconds. */
data class TrackDelays(val audioDelayMs: Int, val subtitleDelayMs: Int)

/**
 * How many evictable rows one account may keep.
 *
 * Without a cap this store grows forever: a 5,000-episode library that remembers a playback speed
 * per episode would leave 5,000 rows on a device that has 5,000 fewer interesting things to do
 * with that space. The value is a guess at "how many distinct things one person is actually in the
 * middle of" with generous headroom — a viewer juggling more than this has long since stopped
 * caring about the audio delay they set on row 301. Eviction is least-recently-used, and *used*
 * includes reads, so a series watched weekly stays warm while a one-off never touched again ages
 * out.
 *
 * [PreferenceEntity.Account] rows are exempt: there is exactly one per user, and it holds the last
 * library, which must not be evicted by episode churn.
 */
const val MAX_PREFERENCE_ROWS_PER_USER: Int = 300

/**
 * Per-item, per-series and per-library playback state, keyed by
 * `(serverUrl, userId, entity, entityId)`.
 *
 * The `(serverUrl, userId)` half is [UserScope], the same isolation `SettingsStore` and
 * `LibraryCacheStore` use: what one viewer was doing with an episode is not the next profile's
 * business. This is deliberately device-local trivia — it is excluded from cloud backup and
 * device transfer in `backup_rules.xml` / `data_extraction_rules.xml`, because an A/V delay that
 * corrects *this* soundbar means nothing on the phone the backup is restored to.
 *
 * Everything is held in one JSON document rather than one preference key per field so that a row
 * can be evicted, re-keyed or counted as a unit. Values inside a row are an untyped
 * `name -> string` map: a write only touches the names it was given, so a field written by a
 * different build survives a read-modify-write of its neighbour instead of being erased by a
 * version that has never heard of it.
 *
 * The primary constructor takes the backing [DataStore] so JVM unit tests can substitute an
 * in-memory implementation; production code uses the [Context] secondary constructor.
 */
class PreferenceStore(
    private val store: DataStore<Preferences>,
    private val maxRowsPerUser: Int = MAX_PREFERENCE_ROWS_PER_USER,
    /**
     * Injectable so tests can hand in their own scheduler. Work parked on the real
     * [Dispatchers.IO] is invisible to `runTest`, so a ViewModel awaiting a read here would resume
     * after the test body finished — touching `Dispatchers.Main` once `resetMain` had already run.
     */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile(FILE_NAME) },
    )

    /** The library the viewer was last in, or null if they have not opened one on this account. */
    suspend fun lastLibraryId(scope: UserScope): String? =
        read(scope, PreferenceEntity.Account, PreferenceEntity.ACCOUNT_ID)[Fields.LAST_LIBRARY]

    suspend fun setLastLibraryId(scope: UserScope, libraryId: String?) {
        write(scope, PreferenceEntity.Account, PreferenceEntity.ACCOUNT_ID) {
            it.putOrRemove(Fields.LAST_LIBRARY, libraryId)
        }
    }

    /** Sort order and view mode a library was last browsed with. */
    suspend fun libraryPresentation(scope: UserScope, libraryId: String): LibraryPresentation? {
        val values = read(scope, PreferenceEntity.Library, libraryId)
        val sort = values[Fields.SORT] ?: return null
        val view = values[Fields.VIEW] ?: return null
        return LibraryPresentation(sort = sort, view = view)
    }

    suspend fun setLibraryPresentation(
        scope: UserScope,
        libraryId: String,
        presentation: LibraryPresentation?,
    ) {
        write(scope, PreferenceEntity.Library, libraryId) {
            it.putOrRemove(Fields.SORT, presentation?.sort)
            it.putOrRemove(Fields.VIEW, presentation?.view)
        }
    }

    /**
     * The audio track the viewer last chose for a series, so episode 2 opens in the same dub they
     * picked for episode 1. Identified by the Jellyfin stream index rendered as a string, because
     * the caller owns what "track" means here and this store must not have an opinion.
     */
    suspend fun seriesAudioTrack(scope: UserScope, seriesId: String): String? =
        read(scope, PreferenceEntity.Series, seriesId)[Fields.AUDIO_TRACK]

    suspend fun setSeriesAudioTrack(scope: UserScope, seriesId: String, track: String?) {
        write(scope, PreferenceEntity.Series, seriesId) { it.putOrRemove(Fields.AUDIO_TRACK, track) }
    }

    /** Playback rate the viewer last used on an item, or null if they never changed it. */
    suspend fun playbackSpeed(scope: UserScope, itemId: String): Float? =
        read(scope, PreferenceEntity.Item, itemId)[Fields.PLAYBACK_SPEED]?.toFloatOrNull()

    suspend fun setPlaybackSpeed(scope: UserScope, itemId: String, speed: Float?) {
        write(scope, PreferenceEntity.Item, itemId) {
            it.putOrRemove(Fields.PLAYBACK_SPEED, speed?.toString())
        }
    }

    /** Lip-sync and subtitle-timing corrections that apply to one item only. */
    suspend fun trackDelays(scope: UserScope, itemId: String): TrackDelays? {
        val values = read(scope, PreferenceEntity.Item, itemId)
        val audio = values[Fields.AUDIO_DELAY_MS]?.toIntOrNull()
        val subtitle = values[Fields.SUBTITLE_DELAY_MS]?.toIntOrNull()
        if (audio == null && subtitle == null) return null
        return TrackDelays(audioDelayMs = audio ?: 0, subtitleDelayMs = subtitle ?: 0)
    }

    suspend fun setTrackDelays(scope: UserScope, itemId: String, delays: TrackDelays?) {
        write(scope, PreferenceEntity.Item, itemId) {
            it.putOrRemove(Fields.AUDIO_DELAY_MS, delays?.audioDelayMs?.toString())
            it.putOrRemove(Fields.SUBTITLE_DELAY_MS, delays?.subtitleDelayMs?.toString())
        }
    }

    /**
     * Quality ceiling pinned to one item — the 4K remux that stutters on this link, capped without
     * touching the account-wide setting.
     *
     * An unrecognised stored id reads back as null rather than as a default. Reads never write, so
     * a value written by a build that knows more qualities than this one is reported as "no
     * opinion" here and stays on disk intact for the build that understands it.
     */
    suspend fun qualityCap(scope: UserScope, itemId: String): PreferredQuality? {
        val stored = read(scope, PreferenceEntity.Item, itemId)[Fields.QUALITY_CAP] ?: return null
        return PreferredQuality.entries.firstOrNull { it.storageId == stored }
    }

    suspend fun setQualityCap(scope: UserScope, itemId: String, quality: PreferredQuality?) {
        write(scope, PreferenceEntity.Item, itemId) {
            it.putOrRemove(Fields.QUALITY_CAP, quality?.storageId)
        }
    }

    /** Drops everything stored for one account. Other profiles on this device are untouched. */
    suspend fun forget(scope: UserScope) = withContext(ioDispatcher) {
        store.edit { preferences ->
            val document = preferences.decode()
            val remaining = document.rows.filterKeys { !it.startsWith(scope.storagePrefix) }
            if (remaining.size == document.rows.size) return@edit
            preferences[Keys.DOCUMENT] = json.encodeToString(document.copy(rows = remaining))
        }
        Unit
    }

    /**
     * Reads one row and marks it as just used, so eviction measures interest rather than age. The
     * touch is skipped when the row is already the most recent one, which is the common case while
     * a single item is being played.
     */
    private suspend fun read(
        scope: UserScope,
        entity: PreferenceEntity,
        entityId: String,
    ): Map<String, String> = withContext(ioDispatcher) {
        val key = rowKey(scope, entity, entityId)
        var values: Map<String, String> = emptyMap()
        store.edit { preferences ->
            val document = preferences.decode()
            val row = document.rows[key] ?: return@edit
            values = row.values
            if (row.usedAt == document.sequence) return@edit
            val sequence = document.sequence + 1
            preferences[Keys.DOCUMENT] = json.encodeToString(
                document.copy(
                    sequence = sequence,
                    rows = document.rows + (key to row.copy(usedAt = sequence)),
                ),
            )
        }
        values
    }

    private suspend fun write(
        scope: UserScope,
        entity: PreferenceEntity,
        entityId: String,
        mutate: (MutableMap<String, String>) -> Unit,
    ) = withContext(ioDispatcher) {
        val key = rowKey(scope, entity, entityId)
        store.edit { preferences ->
            val document = preferences.decode()
            // Starts from what is already stored, so `mutate` clearing one field leaves the rest --
            // including names this build does not know about -- exactly where they were.
            val values = document.rows[key]?.values?.toMutableMap() ?: mutableMapOf()
            mutate(values)
            val sequence = document.sequence + 1
            val rows = document.rows.toMutableMap()
            if (values.isEmpty()) {
                rows.remove(key)
            } else {
                rows[key] = PersistedRow(values = values, usedAt = sequence)
            }
            preferences[Keys.DOCUMENT] = json.encodeToString(
                PersistedPreferences(sequence = sequence, rows = rows).evict(scope),
            )
        }
        Unit
    }

    /** Least-recently-used trim of one account's evictable rows down to [maxRowsPerUser]. */
    private fun PersistedPreferences.evict(scope: UserScope): PersistedPreferences {
        val accountRow = rowKey(scope, PreferenceEntity.Account, PreferenceEntity.ACCOUNT_ID)
        val evictable = rows.filterKeys { it.startsWith(scope.storagePrefix) && it != accountRow }
        val excess = evictable.size - maxRowsPerUser
        if (excess <= 0) return this
        val doomed = evictable.entries
            .sortedBy { it.value.usedAt }
            .take(excess)
            .mapTo(mutableSetOf()) { it.key }
        return copy(rows = rows.filterKeys { it !in doomed })
    }

    private fun rowKey(scope: UserScope, entity: PreferenceEntity, entityId: String): String =
        "${scope.storagePrefix}${entity.storageId}/$entityId"

    private fun Preferences.decode(): PersistedPreferences =
        this[Keys.DOCUMENT]
            ?.let { runCatching { json.decodeFromString<PersistedPreferences>(it) }.getOrNull() }
            ?: PersistedPreferences()

    private fun MutableMap<String, String>.putOrRemove(name: String, value: String?) {
        if (value == null) remove(name) else this[name] = value
    }

    private object Keys {
        val DOCUMENT = stringPreferencesKey("item_preferences_v1")
    }

    private object Fields {
        const val LAST_LIBRARY = "last_library"
        const val SORT = "sort"
        const val VIEW = "view"
        const val AUDIO_TRACK = "audio_track"
        const val PLAYBACK_SPEED = "playback_speed"
        const val AUDIO_DELAY_MS = "audio_delay_ms"
        const val SUBTITLE_DELAY_MS = "subtitle_delay_ms"
        const val QUALITY_CAP = "quality_cap"
    }

    companion object {
        /**
         * Its own DataStore file so the backup rules can exclude exactly this and nothing else.
         */
        const val FILE_NAME: String = "shoumei_preferences"
    }
}

/**
 * [sequence] is a monotonic use counter rather than a wall clock: LRU ordering has to be a total
 * order, and `System.currentTimeMillis()` gives neither uniqueness under rapid writes nor
 * monotonicity across a clock change.
 */
@Serializable
private data class PersistedPreferences(
    val sequence: Long = 0L,
    val rows: Map<String, PersistedRow> = emptyMap(),
)

@Serializable
private data class PersistedRow(
    val values: Map<String, String> = emptyMap(),
    val usedAt: Long = 0L,
)

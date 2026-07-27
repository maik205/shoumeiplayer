# Browse v2 — Implementation Plan

Execution plan for `docs/browse-v2-brief.md`. Each task block is self-contained:
an implementer with **no other context** reads §0, §2 (non-touch), §3 (pinned
contracts) and its own task block, and can do the work. **Do not deviate from §3
signatures** — parallel tasks compile against them. Companions:
`docs/browse-v2-brief.md` (guardrails, not re-litigated), `docs/ui-design.md`
(tokens), `docs/osd-v3.md` §2/§6 (chip idiom + radius amendment),
`docs/jellyfin-api-surface.md`, `docs/plan.md` Appendix A.

## 0. Conventions

Repo root `C:\Users\maik\Documents\Projects\shoumeiplayer`. Shorthand:
`src/**` = `app/src/main/java/com/maik205/shoumeiplayer/**`,
`test/**` = `app/src/test/java/com/maik205/shoumeiplayer/**`,
`res/**` = `app/src/main/res/**`.
Verify commands (repo root, PowerShell): `.\gradlew.bat :app:assembleDebug`
(compile), `.\gradlew.bat :app:testDebugUnitTest` (JVM tests, `--tests
"*<ClassName>*"` for one class). Every task ends green on its stated command; a
wave is not closed until both are green (Opus gate).

---

## 1. Endpoint verification (grepped from `jellyfin-openapi.json`, repo root)

All four questions from the brief are answered against the spec document in the
repo. Casing below is **verbatim** from the spec.

### 1.1 Genre queries

Two independent facilities exist; both are needed.

**A. Enumerate genres — `GET /Genres`** (only `get` is defined on this path).
Response: `#/components/schemas/BaseItemDtoQueryResult` — the *same*
`QueryResult<BaseItemDto>` shape the app already deserializes, each item a genre
whose `Id` is a **uuid** and `Name` the display string. Query params (all
`query`, all optional): `startIndex`, `limit`, `searchTerm`, `parentId`,
`fields`, `excludeItemTypes`, `includeItemTypes`, `isFavorite`,
`imageTypeLimit`, `enableImageTypes`, `userId`, `nameStartsWithOrGreater`,
`nameStartsWith`, `nameLessThan`, `sortBy`, `sortOrder`, `enableImages`,
`enableTotalRecordCount`. There is **no** `/Genres/Names`; `/MusicGenres` is the
audio-only twin and is out of scope.

**B. Filter items by genre — `GET /Items`.** `genres` (array of **string**, genre
*names*) and `genreIds` (array of string `format: uuid`) both exist, alongside
`studios`/`studioIds`/`years` in the same family.

**Decision (pinned): use `genreIds`, never `genres`.** `JellyfinClient.buildUrl`
joins a `List<*>` with `","`; a genre name containing a comma (e.g.
`Action, Adventure` as some scrapers emit) would silently split into two filters.
Uuids cannot contain a comma. `/Genres` hands us the uuid directly, so there is
no reason to round-trip names.

### 1.2 `GET /Search/Hints`

Response: `#/components/schemas/SearchHintResult` =
`{ "SearchHints": SearchHint[], "TotalRecordCount": int }`.
Query params: `startIndex`, `limit`, `userId`, `searchTerm`, `includeItemTypes`,
`excludeItemTypes`, `mediaTypes`, `parentId`, `isMovie`, `isSeries`, `isNews`,
`isKids`, `isSports`, `includePeople`, `includeMedia`, `includeGenres`,
`includeStudios`, `includeArtists`.

`SearchHint` is **not** a `BaseItemDto` — it has its own flat shape:
`ItemId`, `Id`, `Name`, `MatchedTerm`, `Type` (`BaseItemKind`), `ProductionYear`,
`RunTimeTicks`, `IndexNumber`, `ParentIndexNumber`, `PrimaryImageTag`,
`ThumbImageTag`, `ThumbImageItemId`, `BackdropImageTag`, `BackdropImageItemId`,
`Series`, `IsFolder`, `MediaType`, `PrimaryImageAspectRatio`, `EpisodeCount`.
There is **no** `ImageBlurHashes` and no `UserData` on a hint — suggestion tiles
get no blurhash and no progress bar, by design.

### 1.3 `POST /Users/Configuration` (write-back)

Path `/Users/Configuration` defines **`post` only** — there is no GET.
- Query param: `userId` (string, `required` absent ⇒ optional; omitted means the
  authenticated user). We pass it explicitly.
- Request body: `#/components/schemas/UserConfiguration` (also offered as
  `text/json` and `application/*+json`).
- Responses: **`204`**, `401`, `403`, `503`. No response body — use
  `JellyfinClient.postEmpty`, not `post<T>`.

**`UserConfiguration` — all 16 properties** (`additionalProperties: false`, so an
unknown key is a 400): `AudioLanguagePreference` (string?),
`PlayDefaultAudioTrack` (bool), `SubtitleLanguagePreference` (string?),
`DisplayMissingEpisodes` (bool), `GroupedFolders` (array<uuid>), `SubtitleMode`
(`SubtitlePlaybackMode`: `Default | Always | OnlyForced | None | Smart`),
`DisplayCollectionsView` (bool), `EnableLocalPassword` (bool), `OrderedViews`
(array<uuid>), `LatestItemsExcludes` (array<uuid>), `MyMediaExcludes`
(array<uuid>), `HidePlayedInLatest` (bool), `RememberAudioSelections` (bool),
`RememberSubtitleSelections` (bool), `EnableNextEpisodeAutoPlay` (bool),
`CastReceiverId` (string?).

**Read path:** `GET /Users/Me` → `UserDto.Configuration` (nullable). Already
implemented as `AuthRepository.userConfiguration()` with a process-lifetime cache.

**Overwrite semantics (load-bearing):** the POST body *replaces the whole
object*. `src/data/api/dto/AuthDto.kt` models only 6 of the 16 properties today,
and `JellyfinClient` serializes with `encodeDefaults = true`, so posting the
partial DTO would reset the other 10 (library ordering, exclusions, autoplay,
cast receiver) to their defaults server-side. M-B1 completes the DTO; M-B3
implements read-modify-write only.

### 1.4 `GET /Items` — `personIds`

`personIds`: `query`, `array` of `string` `format: uuid`. Siblings:
`person` (single **name** string), `personTypes` (array). `style`/`explode` are
unset in the spec (OpenAPI default `form`); Jellyfin's binder accepts the
comma-delimited form `JellyfinClient.buildUrl` already produces, and uuids are
comma-safe. Person ids come from `BaseItemDto.People[].Id` (already modelled as
`BaseItemPersonDto`) or from `GET /Persons`.

Also confirmed on `/Items`: `filters` (`ItemFilter`: `IsUnplayed`, `IsPlayed`, `IsFavorite`, `IsResumable`, …) and `sortBy` (`ItemSortBy`: `SortName`, `DateCreated`, `PremiereDate`, `CommunityRating`, `Random`, `ProductionYear`, …) — the chip row uses these verbatim.

---

## 2. Explicit non-touch list

The concurrent OSD v3 workflow owns these. **No browse task edits them, for any
reason.** If a browse task appears to need one, stop and report.

- `src/ui/screens/player/**`, `src/player/**`
- `src/data/repo/PlaybackRepository.kt`, `src/data/repo/TrackSelection.kt`
- `src/data/api/ShoumeiDeviceProfile.kt`, `src/data/api/dto/PlaybackDto.kt`, `src/data/api/dto/DeviceProfileDto.kt`
- `test/player/**`, `test/ui/screens/player/**`, `docs/osd-v3.md`

**Shared-with-player, additive-only:** `src/data/api/dto/AuthDto.kt`
(`UserConfigurationDto` is read by `TrackSelection`). M-B1 may **add** properties
with defaults; it must not rename, reorder, retype or remove the six existing
ones. Likewise nothing in `src/ui/theme/**` or `src/ui/components/**` that the
player imports (`Dur`, `Ease`, `focusTween`, `Scrims`, `Color.kt` tokens,
`PosterImage`, `BlurHashPainter`) may be deleted or have its signature changed.

---

## 3. Pinned contracts (copy verbatim)

### 3.1 Routes + NavRail — `src/ui/navigation/Routes.kt`, `src/ui/components/NavRail.kt`

```kotlin
// Routes.kt — ADD this one route; every existing route stays byte-identical.
@Serializable data object LibrariesRoute
```

```kotlin
// src/ui/components/NavRail.kt
enum class NavRailDestination(val labelRes: Int, val icon: ImageVector) {
    Home(R.string.nav_home, Icons.Filled.Home),
    Search(R.string.nav_search, Icons.Filled.Search),
    Libraries(R.string.nav_libraries, Icons.Filled.VideoLibrary),
    Settings(R.string.nav_settings, Icons.Filled.Settings),
}

/** Collapsed 56dp / expanded 220dp left rail. See M-B9 for the anti-trap rules. */
@Composable
fun NavRail(
    selected: NavRailDestination,
    onSelect: (NavRailDestination) -> Unit,
    contentFocusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    railFocusRequester: FocusRequester = remember { FocusRequester() },
)

/**
 * Rail + content frame. [content] gets both requesters so a screen can wire
 * `focusProperties { left = railFocusRequester }` on its leading column and take
 * initial focus on [contentFocusRequester].
 */
@Composable
fun NavRailScaffold(
    selected: NavRailDestination,
    onSelect: (NavRailDestination) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (contentFocusRequester: FocusRequester, railFocusRequester: FocusRequester) -> Unit,
)
```

`Dimens.kt` gains `RailCollapsedWidth = 56.dp`, `RailExpandedWidth = 220.dp`,
`RailItemHeight = 56.dp`. `Dur` gains `HeroDebounce = 320` (guardrail 7 requires
≥300), `HeroCross = 400` (both motion system 4) and `RailExpand = 160` (system 1).
M-B7 owns both files.

### 3.2 SettingsStore — `src/data/session/SettingsStore.kt`

Client-only prefs. New DataStore file (`shoumei_settings`); `SessionStore` is not
modified.

Preference keys (exact strings): `focus_scale_enabled`, `clock_in_osd`
(boolean), `preferred_quality` (string), `autoplay_next_episode` (boolean), in a
`private object SettingsKeys` mirroring `SessionKeys`.

```kotlin
@Immutable
data class ClientSettings(
    val focusScaleEnabled: Boolean = true,
    val clockInOsd: Boolean = true,
    /** Label only: "Auto" | "4K" | "1080p" | "720p" | "480p". Bitrate mapping is player-owned. */
    val preferredQuality: String = "Auto",
    val autoplayNextEpisode: Boolean = true,
)

class SettingsStore(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("shoumei_settings") },
    )
    val settings: Flow<ClientSettings>
    suspend fun current(): ClientSettings
    suspend fun setFocusScaleEnabled(value: Boolean)
    suspend fun setClockInOsd(value: Boolean)
    suspend fun setPreferredQuality(value: String)
    suspend fun setAutoplayNextEpisode(value: Boolean)
}
```

```kotlin
// src/di/AppContainer.kt — ADD one line (M-B4 is sole owner of this file)
val settingsStore: SettingsStore by lazy { SettingsStore(context) }
```

### 3.3 On-screen keyboard grid — `src/ui/screens/search/KeyboardGrid.kt`

**Pure Kotlin, no Compose imports, no Android imports.** Unit-tested.

```kotlin
sealed interface KeyAction {
    data class Char(val value: kotlin.Char) : KeyAction
    data object Space : KeyAction
    data object Delete : KeyAction
    data object Clear : KeyAction
}

@Immutable data class KeyboardKey(val id: String, val label: String, val action: KeyAction, val span: Int = 1)
@Immutable data class KeyboardCursor(val row: Int, val col: Int)
enum class KeyboardDirection { Up, Down, Left, Right }

@Immutable
data class KeyboardGrid(val rows: List<List<KeyboardKey>>) {
    fun keyAt(cursor: KeyboardCursor): KeyboardKey?
    /** Neighbour cursor, or [cursor] unchanged when the move would leave the grid. */
    fun move(cursor: KeyboardCursor, direction: KeyboardDirection): KeyboardCursor

    /** 7 rows: A-F / G-L / M-R / S-X / Y Z 0 1 2 3 / 4 5 6 7 8 9 / [Space 3][Delete 2][Clear 1]. */
    companion object { val Default: KeyboardGrid }
}

/** Pure reducer: the only place the query string is mutated. */
fun applyKey(query: String, action: KeyAction): String
```

`move` clamps the column to the target row's last index and never wraps in either
axis; an unchanged return means "let default focus search take over" (LEFT at col
0 opens the rail, RIGHT at the last col enters the results grid).
`applyKey`: `Char` appends; `Space` appends `' '` unless the query is empty or
already ends in `' '`; `Delete` drops the last character (no-op when empty);
`Clear` returns `""`.

### 3.4 Home hero / focus state — `src/ui/screens/home/HomeViewModel.kt`

```kotlin
@Immutable
data class HeroUi(
    val itemId: String, val title: String, val logoUrl: String?,
    val backdropUrl: String?, val backdropBlurHash: String?,
    val specLine: String,          // ItemMapping.specLine rhythm: year / runtime / rating
    val overview: String?, val resumeTicks: Long, val canResume: Boolean,
)

// HomeRow and HomeUiState keep their current definitions verbatim. HomeUiState
// gains NO hero field: the hero is a separate flow (see below).

class HomeViewModel(
    private val libraryRepository: LibraryRepository,
    private val imageUrlBuilder: ImageUrlBuilder,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState>

    /**
     * Deliberately a SECOND flow, not a field of [HomeUiState]: the hero changes on
     * every card focus and must not recompose the row list (brief guardrail 7).
     * Debounced by Dur.HeroDebounce before it emits.
     */
    val hero: StateFlow<HeroUi?>

    fun onCardFocused(rowKey: String, cardId: String)
    fun retry()
    fun libraryRouteFor(id: String): HomeLibraryTarget?
}
```

Row order (empty rows dropped): Continue watching → Next up → Latest in *view*
(one per `movies`/`tvshows` view) → genre rows (up to 4, from
`genres(parentId = firstMoviesViewId)` then `items(genreIds = listOf(id))`) →
My media. Genre row title is the plain genre name, no prefix.

### 3.5 Chip-row filter model — `src/ui/components/ChipRow.kt` + `src/ui/screens/library/LibraryViewModel.kt`

```kotlin
// ChipRow.kt
@Immutable data class GenreUi(val id: String, val name: String)
@Immutable data class ChipUi(val id: String, val label: String, val selected: Boolean, val hasMenu: Boolean = false)

/** osd-v3 §6 pill amendment: 36dp tall, 18dp radius, 20dp h-padding, 12dp gap. */
@Composable
fun ChipRow(
    chips: List<ChipUi>, onChipClick: (ChipUi) -> Unit,
    modifier: Modifier = Modifier, startPadding: Dp = Dimens.OverscanHorizontal,
)

@Composable
fun PillChip(
    label: String, selected: Boolean, onClick: () -> Unit,
    modifier: Modifier = Modifier, trailingCaret: Boolean = false,
)
```

Pill states (no new colors, no second accent): resting `Ink100` fill, label
`Ash600`; selected `Paper` fill, label `Ink000`; focused 2dp `Lit` border +
`Paper` fill + `Ink000` label (selected+focused = same, border only). `Tungsten`
stays playback-only and never appears on a chip.

```kotlin
// LibraryViewModel.kt — REPLACES the existing LibraryFilter enum.
// LibrarySort stays exactly as it is today (NAME / DATE_ADDED / PREMIERE).
enum class WatchedFilter(val apiValue: String?, val labelRes: Int) {
    All(null, R.string.filter_all),
    Unwatched("IsUnplayed", R.string.filter_unwatched),
    Watched("IsPlayed", R.string.filter_watched),
}

@Immutable
data class ListingFilter(
    val watched: WatchedFilter = WatchedFilter.All,
    val genre: GenreUi? = null,
    val sort: LibrarySort = LibrarySort.NAME,
) {
    fun apiFilters(): List<String> = listOfNotNull(watched.apiValue)
    fun apiGenreIds(): List<String> = listOfNotNull(genre?.id)
}
```

`LibraryUiState` gains: `val filter: ListingFilter`, `val genres: List<GenreUi>`,
`val focused: GridTileUi?` (the metadata strip), and **loses** the old
`filter: LibraryFilter` field. Everything else in `LibraryUiState` is unchanged.

### 3.6 Repository additions

```kotlin
// src/data/api/dto/SearchHintDto.kt — NEW
@Serializable data class SearchHintResult(
    @SerialName("SearchHints") val searchHints: List<SearchHintDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = 0,
)
@Serializable data class SearchHintDto(
    @SerialName("ItemId") val itemId: String = "",
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("ThumbImageTag") val thumbImageTag: String? = null,
    @SerialName("ThumbImageItemId") val thumbImageItemId: String? = null,
    @SerialName("Series") val series: String? = null,
    @SerialName("IsFolder") val isFolder: Boolean = false,
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
)

/** SearchHint carries no blurhash and no UserData: map to GridTileUi with both null. */
```

```kotlin
// src/data/repo/LibraryRepository.kt — ADD (existing methods untouched)

/** GET /Genres. Items are genres: `id` is a uuid usable as `genreIds`. */
suspend fun genres(parentId: String? = null, limit: Int = 40): ApiResult<List<BaseItemDto>>

/** GET /Search/Hints. Media only by default; people/genres/studios are opt-in. */
suspend fun searchHints(
    term: String,
    limit: Int = 20,
    includePeople: Boolean = false,
    includeGenres: Boolean = false,
    includeStudios: Boolean = false,
): ApiResult<List<SearchHintDto>>

/**
 * GET /Items — APPEND these three params to the existing `items(...)` signature,
 * after `searchTerm`. All defaulted, so every current call site keeps compiling.
 * Nothing already in that signature may be renamed, reordered or retyped.
 * Use genreIds (uuid), never genres (name).
 */
    genreIds: List<String> = emptyList(),
    personIds: List<String> = emptyList(),
    nameStartsWith: String? = null,
```

```kotlin
// src/data/repo/AuthRepository.kt — ADD

/** GET /Users/Me, bypassing the cache; refreshes it on success. */
suspend fun refreshUserConfiguration(): ApiResult<UserConfigurationDto>

/**
 * Read-modify-write. NEVER constructs a fresh UserConfigurationDto: refresh the
 * whole 16-property object, apply [transform], POST the WHOLE object to
 * /Users/Configuration?userId=..., replace the cache on 204. A read failure
 * aborts without writing.
 */
suspend fun updateUserConfiguration(
    transform: (UserConfigurationDto) -> UserConfigurationDto,
): ApiResult<UserConfigurationDto>
```

```kotlin
// src/data/api/dto/AuthDto.kt — UserConfigurationDto gains the 10 missing
// properties. Existing six keep their names, order, types and defaults.
@SerialName("DisplayMissingEpisodes") val displayMissingEpisodes: Boolean = false,
@SerialName("GroupedFolders") val groupedFolders: List<String> = emptyList(),
@SerialName("DisplayCollectionsView") val displayCollectionsView: Boolean = false,
@SerialName("EnableLocalPassword") val enableLocalPassword: Boolean = false,
@SerialName("OrderedViews") val orderedViews: List<String> = emptyList(),
@SerialName("LatestItemsExcludes") val latestItemsExcludes: List<String> = emptyList(),
@SerialName("MyMediaExcludes") val myMediaExcludes: List<String> = emptyList(),
@SerialName("HidePlayedInLatest") val hidePlayedInLatest: Boolean = true,
@SerialName("EnableNextEpisodeAutoPlay") val enableNextEpisodeAutoPlay: Boolean = true,
@SerialName("CastReceiverId") val castReceiverId: String? = null,
```

---

## 4. Task blocks

### M-B1 — DTO completion + SearchHint DTOs · **Sonnet**
**Depends:** none. **Files:** `src/data/api/dto/AuthDto.kt` (modify, additive),
`src/data/api/dto/SearchHintDto.kt` (create).
Add the 10 missing `UserConfigurationDto` properties and both SearchHint classes
exactly as §3.6. Add a KDoc on `UserConfigurationDto` stating: *POST
/Users/Configuration replaces the whole object; never build this instance from
scratch for a write.*
**Verify:** `.\gradlew.bat :app:assembleDebug`.
**Done:** compiles; `TrackSelection` and `PlayerViewModel` untouched and still building.

### M-B2 — LibraryRepository: genres / searchHints / genreIds / personIds · **Sonnet**
**Depends:** M-B1. **Files:** `src/data/repo/LibraryRepository.kt`.
Implement the three §3.6 members. `genres()` → `GET /Genres` with
`userId`, `parentId`, `limit`, `sortBy=SortName`, `enableImages=false`,
`enableTotalRecordCount=false`, mapped to `items`. `searchHints()` → `GET
/Search/Hints` with `userId`, `searchTerm`, `limit`, `includeMedia=true`,
`includePeople`, `includeGenres`, `includeStudios`, mapped to `searchHints`.
`items()` appends `genreIds`, `personIds`, `nameStartsWith` (each dropped when
empty/null — reuse the existing `ifEmpty { null }` idiom so the URL never gains
a blank param). Every method fails fast via the existing `userIdOrFail()` guard.
**Verify:** `.\gradlew.bat :app:assembleDebug`.
**Done:** compiles; no existing call site changed.

### M-B3 — UserConfiguration read-modify-write · **Sonnet**
**Depends:** M-B1. **Files:** `src/data/repo/AuthRepository.kt`.
Implement `refreshUserConfiguration()` and `updateUserConfiguration(transform)`
per §3.6. The write is `client.postEmpty("/Users/Configuration", body = merged,
params = mapOf("userId" to userId))` — 204 has no body, so `post<T>` would fail
deserialization. Keep `userConfiguration()`'s cache semantics; a successful write
replaces `cachedConfiguration` with the merged object, a failed one leaves it.
`logout()` must also clear it (already does).
**Verify:** `.\gradlew.bat :app:assembleDebug`.
**Done:** compiles; player call sites unchanged.

### M-B4 — SettingsStore + container wiring · **Sonnet** · sole owner of `AppContainer.kt`
**Depends:** none. **Files:** `src/data/session/SettingsStore.kt` (create),
`src/di/AppContainer.kt` (modify — one `by lazy` line, nothing else).
Implement §3.2 verbatim. Mirror `SessionStore`'s two-constructor shape so JVM
tests can inject an in-memory `DataStore<Preferences>`.
**Verify:** `.\gradlew.bat :app:assembleDebug`.
**Done:** compiles; `shoumei_session` file name untouched.

### M-B5 — Keyboard grid model (pure) · **Sonnet**
**Depends:** none. **Files:** `src/ui/screens/search/KeyboardGrid.kt` (create).
Implement §3.3. No Compose/Android imports beyond `androidx.compose.runtime.Immutable`.
**Verify:** `.\gradlew.bat :app:assembleDebug`.
**Done:** compiles; file has no `androidx.compose.ui` import.

### M-B6 — Tests: repos, config write-back, keyboard model · **Sonnet**
**Depends:** M-B2, M-B3, M-B5. **Files:**
`test/data/LibraryRepositoryTest.kt` (extend), `test/data/UserConfigurationTest.kt`
(create), `test/ui/screens/search/KeyboardGridTest.kt` (create).
Use the existing `FakeJellyfin` MockEngine harness. Required cases:
1. `genres()` parses `{ "Items": [...] }` and surfaces `Id`/`Name`.
2. `items(genreIds = listOf("a","b"))` produces `genreIds=a,b` in the request URL
   and **no** `genres=` param; `items(personIds = ...)` likewise.
3. `searchHints()` parses `SearchHints` and tolerates a missing `TotalRecordCount`.
4. `updateUserConfiguration { it.copy(subtitleMode = "Always") }` issues **one**
   GET `/Users/Me` then **one** POST whose body contains **all 16** keys, with
   `OrderedViews`/`MyMediaExcludes` echoed back unchanged from the GET fixture
   (this is the regression test for the overwrite hazard).
5. A GET failure means **no** POST is issued.
6. `applyKey` for each `KeyAction`; `move()` clamping into a shorter last row;
   `move()` returning the same cursor at each of the four edges.
**Verify:** `.\gradlew.bat :app:testDebugUnitTest`.
**Done:** all green.

### M-B7 — Tokens, strings, design-doc amendment · **Sonnet** · sole owner of `strings.xml`, `Dimens.kt`, `Motion.kt`, `docs/ui-design.md`
**Depends:** none. **Files:** `res/values/strings.xml`, `src/ui/theme/Dimens.kt`,
`src/ui/theme/Motion.kt`, `docs/ui-design.md`.
Add the §3.1 `Dimens` and `Dur` constants. Add **every** string the overhaul
needs in one pass, so no later task touches this file: `nav_home`, `nav_search`,
`nav_libraries`, `nav_settings`, `filter_all`, `filter_unwatched`,
`filter_watched`, `filter_genre`, `filter_sort`, `sort_name`, `sort_date_added`,
`sort_release_date`, `key_space`, `key_delete`, `key_clear`, `suggested`,
`more_info`, `type_to_search`, `settings_playback`, `settings_appearance`,
`settings_server`, `settings_about`, `settings_preferred_quality`,
`settings_subtitle_mode`, `settings_subtitle_language`, `settings_audio_language`,
`settings_play_default_audio`, `settings_autoplay_next`,
`settings_remember_selections`, `settings_focus_scale`, `settings_clock_in_osd`,
`settings_open_source_notices`.
In `docs/ui-design.md`, extend the §4.1 radius note and §4.3 chip note: the
osd-v3 §6 pill amendment now also covers **browse filter chips** (18dp radius,
36dp tall) and the **nav rail** (rail item pill, 18dp radius). Text chips remain
the idiom for season chips and track lists. Record that `Tungsten` is still
playback-only and never colours a chip.
**Verify:** `.\gradlew.bat :app:assembleDebug`.
**Done:** compiles; no duplicate string names; taste rules (sentence case, no
em-dashes, no eyebrows) hold in the new strings.

### M-B8 — ChipRow / PillChip component · **Sonnet**
**Depends:** M-B7. **Files:** `src/ui/components/ChipRow.kt` (create).
Implement §3.5. `ChipRow` is a `LazyRow` with `focusRestorer()`,
`contentPadding` = start `Dimens.OverscanHorizontal`, top/bottom 8dp so the focus
rim is not clipped, `Arrangement.spacedBy(12.dp)`. `PillChip` uses `FocusSurface`
conventions: `indication = null`, no ripple, no scale (a chip is a full-height
control, per §4.3 the row rule) — do **not** use `FocusScale`/`shoumeiFocus`;
focus is carried by the 2dp `Lit` border alone, animated with
`focusTween(focused)`, never the deprecated `borderTween`.
`trailingCaret` draws a 12dp chevron for menu-opening chips (Genres, Sort).
Leave `TextChip` in `LibraryGrid.kt` in place — it is still used by Detail
season chips.
**Verify:** `.\gradlew.bat :app:assembleDebug`.
**Done:** compiles; no new color constants introduced.

### M-B9 — NavRail component · **Sonnet**
**Depends:** M-B7. **Files:** `src/ui/components/NavRail.kt` (create).
Implement §3.1. Collapsed: 56dp wide, icons 24dp `@0.55`, no labels, no
background beyond `Ink000`. Expanded (any rail item focused): animate to 220dp
over `Dur.RailExpand`, labels `titleMedium`, selected item `Paper`, others
`Ash600`. Focused item takes the §4.3 full-width-row treatment: `Ink050`
background + `3.dp × RailItemHeight` `Lit` edge-light bar, **no scale**.
Anti-trap requirements, all mandatory:
- `focusProperties { right = contentFocusRequester }` on every rail item.
- The rail column carries `focusRestorer()`; it does **not** take initial focus —
  content does.
- Selecting an item calls `onSelect` and immediately hands focus to
  `contentFocusRequester`.
- `onPreviewKeyEvent` on the rail: `KEYCODE_BACK` moves focus to content and
  consumes the event **only while the rail has focus**; otherwise it is passed on.
**Verify:** `.\gradlew.bat :app:assembleDebug`.
**Done:** compiles. Manual D-pad check deferred to M-B15.

### M-B10 — Home v2: billboard hero + genre rows · **Opus**
**Depends:** M-B2, M-B8, M-B9. **Files:** `src/ui/screens/home/HomeViewModel.kt`, `src/ui/screens/home/HomeScreen.kt`.

ViewModel per §3.4: keep the existing parallel `async` load; add genre rows (`genres(parentId = <first movies view id>)`, take the first 4 by `SortName`, one `items(genreIds = listOf(it.id), limit = 20, sortBy = "Random")` each, drop empties); add the debounced `hero` flow. Default hero before first focus: first Continue-watching item, else first Next-up, else first Latest.

Screen: **delete `HomeTopBar`, `GlyphAction` and both glyph buttons** — the rail replaces them. Delete the screen-local `focusedItem` / `ambientItem` pair and its `LaunchedEffect(focusedItem) { delay(Dur.AmbientDebounce) }` (that debounce moves into the VM), and let `AmbientBackdrop` / `AmbientHeader` be superseded by the hero layers. Keep the `firstCard` `FocusRequester` + `withFrameNanos` retry loop verbatim — it is the only thing landing first-frame focus on a lazy row. Layers bottom-up: (0) full-bleed hero backdrop `Crop` alpha 0.55 under a `Scrims.DetailWipe`-style left wipe + bottom settle; (1) hero copy column at x=48 (after the rail inset) — logo image capped at 96dp height with the title as `displayLarge` fallback, spec line, 3-line overview, `Play` / `More info` slabs; (2) the `LazyColumn` of `MediaRow`s, first row title anchored so the hero owns the top ~52% of the canvas.

**Perf (guardrail 7, non-negotiable):** the backdrop is its own composable reading `hero` via `collectAsStateWithLifecycle()` *inside itself* — `HomeScreen` must not read `hero`; use `Crossfade(targetState = hero, animationSpec = tween(Dur.HeroCross, Ease.Decel))`, never a sliding `AnimatedContent`; card focus routes through `MediaRow`'s existing `onItemFocused` to `viewModel.onCardFocused(rowKey, cardId)`, with no new per-card lambda in the item body; rows stay lazy.

Rail: wrap in `NavRailScaffold(selected = NavRailDestination.Home, ...)`; the first card of row 0 takes `contentFocusRequester` and `focusProperties { left = railFocusRequester }`.
**Verify:** `.\gradlew.bat :app:assembleDebug`. **Done:** compiles; hero recomposition is confined to the backdrop + copy column.

### M-B11 — Library v2: chip row + focus metadata strip · **Sonnet**
**Depends:** M-B2, M-B8, M-B9. **Files:** `src/ui/screens/library/LibraryViewModel.kt`, `src/ui/screens/library/LibraryScreen.kt`, `src/ui/screens/library/LibraryGrid.kt` (focus callback only; tile visuals do not change).

ViewModel: swap `LibraryFilter` for `ListingFilter` (§3.5), load `genres(parentId = route.libraryId)` alongside the first page, re-query from `startIndex = 0` on any filter change, add `onTileFocused(tile: GridTileUi)`.

Screen: the pinned header keeps the library name at `displayMedium`; under it a `ChipRow` at y=140 with `All · Unwatched · Watched · <Genre> ▸ · <Sort> ▸`. Genre and Sort chips open the existing dialog idiom (`Ink150` plate, 4dp radius, `1.dp Lit @8%` hairline) listing options as pill rows. Above the grid, a one-line **metadata strip** with the focused tile's title + spec line (`bodyMedium @ Ash600`); per-tile labels are unchanged. Replace the old `LibraryHeader` `TextChip` row entirely.

`PosterGrid` gains exactly one new defaulted parameter, `onTileFocused: (GridTileUi) -> Unit = {}`, so Search keeps compiling this wave; its existing `firstTileFocus` / `rowZeroUp` / `state` params must not be renamed. `PosterGridTile` reports focus upward through it; visuals, the 1.04 scale and `GRID_COLUMNS = 5` are unchanged. While in this file, replace the `@Deprecated` `focusScaleTween` / `veilTween` / `borderTween` calls with `focusTween`.

Rail: `NavRailScaffold(selected = NavRailDestination.Libraries, ...)`. The alphabet scrubber is **out of scope** — a stretch goal no task owns.
**Verify:** `.\gradlew.bat :app:assembleDebug`. **Done:** compiles; a chip change re-queries from index 0 and the grid keeps its `focusRestorer()`.

### M-B12 — Search v2: D-pad keyboard + suggested row · **Sonnet**
**Depends:** M-B2, M-B5, M-B9, M-B11. **Files:** `src/ui/screens/search/SearchScreen.kt`, `src/ui/screens/search/SearchViewModel.kt`.

ViewModel: keep the 350ms debounce pipeline and `MIN_QUERY_LENGTH` exactly as they are. Add `val suggestions: StateFlow<List<GridTileUi>>` from `searchHints(term = "", limit = 20)` when the query is blank, falling back to `resumeItems()` mapped to tiles if the server rejects a blank hint term. Add `fun onKey(action: KeyAction)` routing through `applyKey` then `onQueryChange`.

Screen: **remove the `BasicTextField`** and its IME entirely (keep the private `SearchGlyph` canvas). Left column, width 360dp: `KeyboardGrid.Default` as a non-lazy `Column` of `Row`s of 48dp keys; the cursor is app-owned (`KeyboardCursor` in `rememberSaveable`) and each key stays individually focusable so the platform focus rim remains truthful. The live query renders above the keyboard at `displayMedium` with a `Tungsten` caret block — display text only, no `TextField`, no `keyboardOptions`, nothing that can raise the IME. Right column: the existing `PosterGrid`, replaced by a "Suggested" `MediaRow` under the shared `RowHeader` while the query is blank. Focus: `focusProperties { right = gridFirst }` on the rightmost key column, `left = keyboardLast` on grid column 0, and LEFT from keyboard column 0 falls through to the rail.

Rail: `NavRailScaffold(selected = NavRailDestination.Search, ...)`.
**Verify:** `.\gradlew.bat :app:assembleDebug`. **Done:** compiles; `grep -r "TextField" src/ui/screens/search` returns nothing.

### M-B13 — Settings v2: two-pane groups + server write-back · **Sonnet**
**Depends:** M-B3, M-B4, M-B7, M-B8, M-B9. **Files:** `src/ui/screens/settings/SettingsViewModel.kt`, `src/ui/screens/settings/SettingsScreen.kt`.

ViewModel: `SettingsViewModel(sessionStore, settingsStore, authRepository, appVersion, engineName)`; `enum class SettingsGroup { Playback, Appearance, Server, About }`. State gains `group`, the `ClientSettings` snapshot, the `UserConfigurationDto` snapshot and `saving: Boolean`. Server-backed fields all go through `authRepository.updateUserConfiguration { }`: subtitle mode (the 5 `SubtitlePlaybackMode` values), subtitle language, audio language, `PlayDefaultAudioTrack`, `EnableNextEpisodeAutoPlay`, `RememberAudioSelections` + `RememberSubtitleSelections`. Client-only fields go through `SettingsStore`: preferred quality, focus scale, clock in OSD. Optimistic UI is **banned**: the row keeps the previous value until the POST returns 204, and on failure reverts and shows the inline error line (§6, never a toast).

Screen: left group list (240dp, §4.3 full-width-row focus treatment, no scale), right field pane. Sign out stays the last row of the Server group with its confirmation dialog, unchanged; `onSignedOut` / `onBack` keep their current signatures.

Rail: `NavRailScaffold(selected = NavRailDestination.Settings, ...)`.
**Verify:** `.\gradlew.bat :app:assembleDebug`. **Done:** compiles; no settings write constructs a `UserConfigurationDto()`.

### M-B14 — Integration: NavGraph + scaffold + rail plumbing · **Opus** · sole owner of `NavGraph.kt`, `Routes.kt`, `MainActivity.kt`
**Depends:** M-B10, M-B11, M-B12, M-B13. **Files:** `src/ui/navigation/Routes.kt`, `src/ui/navigation/NavGraph.kt`, `src/ui/screens/libraries/LibrariesScreen.kt` (create), `src/MainActivity.kt` (only if unavoidable — see below).

Add `LibrariesRoute` and its `composable<LibrariesRoute>`, rendering a libraries grid from `LibraryRepository.userViews()` (reuse `PosterGrid`). Rewire `HomeScreen` to drop `onNavigateToSearch` / `onNavigateToSettings` and take `onNavigate: (NavRailDestination) -> Unit` instead; the same callback is passed to Library, Search and Settings. Rail navigation uses
`navigate(route) { launchSingleTop = true; popUpTo(HomeRoute) { saveState = true }; restoreState = true }`
so rail hops do not stack.
**MainActivity decision:** the rail lives **inside each screen** via
`NavRailScaffold`, not around the `NavHost`, because Detail and Player must render
full-bleed with no rail. Therefore `MainActivity.kt` is expected to need **no
change**; if the implementer finds one is required, they own the file this wave
and must record why in the task's completion note.
**Verify:** `.\gradlew.bat :app:assembleDebug` + `.\gradlew.bat :app:testDebugUnitTest`.
**Done:** every route reachable; Detail and Player render with no rail.

### M-B15 — Gate: D-pad, perf, taste · **Opus**
**Depends:** M-B14. **Files:** none (report only; fixes are filed as follow-ups scoped to a single file each). Checklist, each item pass/fail with evidence:
1. Every element reachable by D-pad on Home, Libraries, Library, Search, Settings.
2. Rail: LEFT from a leading element opens it, RIGHT/CENTER always returns focus
   to content, BACK never strands focus, and no screen starts with rail focus.
3. Hero: focus a card, hold a direction — backdrop commits once per settle, not
   per card; no full-screen recomposition (verify by inspection of which
   composables read `hero`).
4. Search raises no IME on any key press; the physical/remote keyboard still
   types into nothing (no crash).
5. `/Users/Configuration` round-trip: change subtitle mode, restart the app,
   value persists; `OrderedViews` on the server is unchanged (compare a GET
   `/Users/Me` before and after).
6. Tokens: no new hex outside `Color.kt`, no second accent, `Tungsten` only on
   playback state, radii are 4dp or the sanctioned 18dp pill.
7. Overscan: nothing inside 48×27dp; focus rims not clipped in any lazy container.
8. `assembleDebug` + `testDebugUnitTest` green.

---

## 5. Wave table (parallelism + single-owner rule)

| Wave | Tasks (∥ = parallel) | Model | Shared-file owner this wave |
|---|---|---|---|
| W-B1 | M-B1 ∥ M-B5 ∥ M-B7 | Sonnet ×3 | `strings.xml`, `Dimens.kt`, `Motion.kt`, `docs/ui-design.md` → **M-B7** |
| W-B2 | M-B2 ∥ M-B3 ∥ M-B4 | Sonnet ×3 | `AppContainer.kt` → **M-B4** |
| W-B3 | M-B6 ∥ M-B8 ∥ M-B9 | Sonnet ×3 | none shared |
| W-B4 | M-B10 ∥ M-B11 ∥ M-B12 ∥ M-B13 | **Opus** (M-B10) + Sonnet ×3 | `LibraryGrid.kt` → **M-B11** |
| W-B5 | M-B14 | **Opus** | `NavGraph.kt`, `Routes.kt`, `MainActivity.kt` → **M-B14** |
| W-B6 | M-B15 | **Opus** | none (report only) |

**Single-owner rule.** For the whole overhaul, exactly one task ever edits each of these, and only in the wave named: `NavGraph.kt`, `Routes.kt` and `MainActivity.kt` (the last only if unavoidable) → M-B14 (W-B5); `di/AppContainer.kt` → M-B4 (W-B2); `res/values/strings.xml`, `ui/theme/Dimens.kt`, `ui/theme/Motion.kt`, `docs/ui-design.md` → M-B7 (W-B1); `data/api/dto/AuthDto.kt` → M-B1 (W-B1); `ui/screens/library/LibraryGrid.kt` → M-B11 (W-B4).

Waves 1..6 start only after the **OSD v3 gate is green** (brief §Sequencing). An Opus gate closes every wave: read the diff, run both verify commands, confirm the wave's shared-file owner was the only editor of that file.

## 6. Model assignment summary

**Sonnet** — M-B1..M-B9, M-B11, M-B12, M-B13: bounded, single-surface, contract-pinned.
**Opus** — M-B10 (hero focus plumbing + perf), M-B14 (NavGraph/scaffold integration), M-B15 (final gate), plus the close-out gate on every wave: cross-file reasoning, focus/recomposition judgement, taste arbitration.

---

## 7. Risks

1. **`/Users/Configuration` overwrite (highest).** The POST replaces the whole
   16-property object; today's DTO models six. A naive write silently resets the
   user's library ordering, exclusions and autoplay. Mitigations: M-B1 completes
   the DTO, M-B3 is read-modify-write only, M-B6 case 4 asserts all 16 keys are
   echoed, M-B15 item 5 verifies live. Secondary: `additionalProperties: false`
   turns a typo'd `@SerialName` into a 400, so the test must assert exact keys.
2. **Hero backdrop perf.** A 1280px crossfade per card focus drops frames on an
   older TV SoC. Mitigations: `Dur.HeroDebounce = 320` (brief requires ≥300); the
   `hero` flow is separate from `HomeUiState` so rows never recompose; only the
   backdrop composable reads it; `Crossfade` over `Dur.HeroCross`; blurhash
   placeholder; Coil `maxWidth = 1280`, never 1920.
3. **Focus-rail traps.** The classic TV focus sink: RIGHT lands nowhere, or BACK
   exits the app from the rail. Mitigations: the explicit
   `focusProperties { right = contentFocusRequester }` contract, the rail never
   taking initial focus, selection handing focus back, the scoped BACK preview
   handler, M-B15 item 2. `NavRailScaffold` wraps four screens, so a defect is
   systemic — which is exactly why it is one component, gated once.
4. **IME vs. D-pad grid on TV.** Android TV raises the leanback IME from any
   focused `TextField`, landing on top of the custom keyboard. Mitigation: M-B12
   deletes `BasicTextField` outright and renders the query as read-only text; the
   verify step greps for it. Watch `rememberSaveable` cursor state across config
   change, and a physical remote keyboard emitting key events with no field to
   receive them (must not crash).
5. **Genre filtering by name.** `buildUrl` comma-joins lists, so `genres=` breaks
   on any genre name containing a comma. Mitigation: `genreIds` only; M-B6 case 2
   asserts no `genres=` param is ever emitted.
6. **Chip idiom vs. §4.3.** `ui-design.md` §4.3 specifies container-less text
   chips; the brief mandates the osd-v3 pill idiom for browse chips. Unrecorded,
   that reads as a token violation at review. Mitigation: M-B7 records the
   amendment **before** M-B8 builds the component.
7. **Concurrent player workflow collision.** `AuthDto.kt` is read by
   `TrackSelection`. Mitigation: the §2 additive-only rule; M-B1's Done criterion
   is that the player files still compile untouched.
8. **Search hints on a blank term.** The spec does not mark `searchTerm` required,
   but blank-term behaviour is unverified. Mitigation: M-B12 ships the
   `resumeItems()` fallback in the same task, so an empty or 400 response degrades
   to a populated Suggested row, not an empty screen.

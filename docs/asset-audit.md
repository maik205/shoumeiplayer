# Asset Fidelity Audit — what Jellyfin serves vs. what Shoumei renders

Sources: `docs/jellyfin-api-surface.md`, `jellyfin-openapi.json` (`BaseItemDto`, `ImageType`,
`ItemFields`, `ChapterInfo`, `TrickplayInfoDto`, `BaseItemPerson`, `/Items/{id}/Images/*`),
and the current tree (`data/api/dto/BaseItemDto.kt`, `data/ImageUrlBuilder.kt`,
`data/repo/LibraryRepository.kt`, `ui/components/**`, `ui/screens/**`).

Aesthetic authority is `docs/ui-design.md`; every task below cites the section it serves.

---

## 1. Inventory — served vs. used

Legend: **✗** not wired at all · **~** partially wired · **✓** faithful.

| # | Asset / metadata | Jellyfin source | `fields=` gate | Current usage | Gap |
|---|---|---|---|---|---|
| 1 | Primary image | `ImageTags["Primary"]` → `/Items/{id}/Images/Primary` | none | Home posters, Library/Search tiles, Detail poster fallback, Episode thumbs | ✓ |
| 2 | Backdrop **0** | `BackdropImageTags[0]` → `/Items/{id}/Images/Backdrop` | none | Home ambient wash, Detail stage | ~ index 0 only |
| 3 | Backdrop **1..n** | `BackdropImageTags[n]` → `/Items/{id}/Images/Backdrop/{n}` | none | never requested (`imageTypeLimit=1` caps it) | ✗ |
| 4 | Parent/series backdrop | `ParentBackdropItemId` + `ParentBackdropImageTags` | none | not in the DTO; `ItemMapping` guesses `images.backdrop(seriesId, null)` — **untagged, uncached, 404s when the series has none** | ✗ |
| 5 | Thumb | `ImageTags["Thumb"]` | none | wide cards only (`toCardUi(wide=true)`) | ~ |
| 6 | Series/parent Thumb | `SeriesThumbImageTag`, `ParentThumbItemId`+`ParentThumbImageTag` | none | `parentThumbImageTag` is read but paired with the **episode's own id** (`ItemMapping:38`, `LibraryGrid:103`, `DetailViewModel:164`) — wrong item, broken URL | ✗ |
| 7 | Logo | `ImageTags["Logo"]`, `ParentLogoItemId`+`ParentLogoImageTag` | none | nothing | ✗ |
| 8 | Banner | `ImageTags["Banner"]` | none | nothing | ✗ (no surface wants it — **won't fix**) |
| 9 | Art | `ImageTags["Art"]`, `ParentArtItemId`+`ParentArtImageTag` | none | nothing | ✗ (**won't fix** — Logo supersedes) |
| 10 | Disc / Box / BoxRear / Menu | `ImageTags[…]` | none | nothing | ✗ (**won't fix** — no physical-media idiom in §5) |
| 11 | Screenshot | `ScreenshotImageTags[]` | none | nothing | ✗ (follow-up) |
| 12 | `ImageBlurHashes` | `BaseItemDto.ImageBlurHashes` (`type → {tag → hash}`) | none | nothing — `PosterImage` KDoc asserts "no blurhash exists on the server" (false) | ✗ **biggest visual gap** |
| 13 | `PrimaryImageAspectRatio` | `BaseItemDto` | `PrimaryImageAspectRatio` | nothing — poster aspect hardcoded 2:3 | ✗ |
| 14 | Parent primary fallback | `ParentPrimaryImageItemId` + `ParentPrimaryImageTag` | none | nothing | ✗ |
| 15 | `SeriesPrimaryImageTag` | `BaseItemDto` | none | read, but paired with the episode id (same bug as #6) | ✗ |
| 16 | People + `PrimaryImageTag` | `BaseItemDto.People[]` (`BaseItemPerson`) | **`People`** | nothing | ✗ |
| 17 | Chapters + `ImageTag` | `BaseItemDto.Chapters[]` (`ChapterInfo`) | **`Chapters`** | nothing | ✗ |
| 18 | Trickplay manifest | `BaseItemDto.Trickplay` (`{srcId → {width → TrickplayInfoDto}}`) | **`Trickplay`** | nothing | ✗ (follow-up, §5 spec gap) |
| 19 | Taglines | `BaseItemDto.Taglines[]` | **`Taglines`** | nothing | ✗ |
| 20 | Genres | `BaseItemDto.Genres[]` | **`Genres`** | in DTO, never requested, never rendered | ✗ |
| 21 | Studios | `BaseItemDto.Studios[]` (`NameGuidPair`) | **`Studios`** | nothing | ✗ |
| 22 | OfficialRating | `BaseItemDto` | none | Detail spec line + `RATING` rail row | ✓ |
| 23 | CommunityRating | `BaseItemDto` | none | Detail rail `SCORE`, only if <6 rows | ~ |
| 24 | CriticRating | `BaseItemDto` | none | nothing | ✗ |
| 25 | ProductionYear | `BaseItemDto` | none | card subtitles, Detail spec line | ✓ |
| 26 | EndDate / Status | `BaseItemDto` | none | nothing — Series shows no year range or `ENDED` | ✗ |
| 27 | OriginalTitle | `BaseItemDto` | none | nothing | ✗ (follow-up) |
| 28 | MediaStreams | `BaseItemDto.MediaStreams[]` | **`MediaStreams`** | `buildSpecRows(streams=…)` exists but is **always called with `emptyList()`** — AUDIO/SUBS/CODEC rails are dead code | ✗ |
| 29 | UserData | `UserData` | `enableUserData` | Home/Library/Detail. **Search omits `enableUserData`** → watched dim + progress never render on Search | ~ |

### Per-screen roll-up

| Screen | Renders today | Missing |
|---|---|---|
| Home | Primary, Thumb, Backdrop[0], progress, watched | blurhash, Logo, parent-backdrop chain, tagline in ambient header |
| Detail | Backdrop[0], Primary fallback, overview, year/runtime/rating, resume, episode thumbs | Logo hero, tagline, genres, cast row, CriticRating, EndDate, MediaStreams rail, extra backdrops |
| Player | title, timecodes, progress | chapter markers + chapter name, trickplay |
| Library | Primary, progress, watched | blurhash, aspect ratio, thumb fallback |
| Search | Primary, eyebrow | **UserData entirely**, blurhash |

---

## 2. Pinned contracts

### 2.1 `data/api/dto/BaseItemDto.kt` — additions (append to the existing constructor)

```kotlin
    // --- image tags & fallbacks (§5.1/§5.2 art chains) ---
    @SerialName("ImageBlurHashes") val imageBlurHashes: Map<String, Map<String, String>> = emptyMap(),
    @SerialName("PrimaryImageAspectRatio") val primaryImageAspectRatio: Double? = null,
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropImageTags: List<String> = emptyList(),
    @SerialName("ParentLogoItemId") val parentLogoItemId: String? = null,
    @SerialName("ParentLogoImageTag") val parentLogoImageTag: String? = null,
    @SerialName("ParentPrimaryImageItemId") val parentPrimaryImageItemId: String? = null,
    @SerialName("ParentPrimaryImageTag") val parentPrimaryImageTag: String? = null,
    @SerialName("SeriesThumbImageTag") val seriesThumbImageTag: String? = null,
    @SerialName("ScreenshotImageTags") val screenshotImageTags: List<String> = emptyList(),
    // --- metadata (§5.2 copy column + spec rail) ---
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("Taglines") val taglines: List<String> = emptyList(),
    @SerialName("CriticRating") val criticRating: Float? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("Studios") val studios: List<NameGuidPairDto> = emptyList(),
    @SerialName("People") val people: List<BaseItemPersonDto> = emptyList(),
    @SerialName("Chapters") val chapters: List<ChapterInfoDto> = emptyList(),
    @SerialName("MediaStreams") val mediaStreams: List<MediaStreamDto> = emptyList(),
    @SerialName("Trickplay") val trickplay: Map<String, Map<String, TrickplayInfoDto>> = emptyMap(),
```

### 2.2 `data/api/dto/ItemAssetDto.kt` — new file

```kotlin
package com.maik205.shoumeiplayer.data.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `BaseItemDto.People[]` — cast/crew with a portrait tag. Fetched via `fields=People`. */
@Serializable
data class BaseItemPersonDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("Role") val role: String? = null,
    /** `PersonKind`: Actor, Director, Writer, Producer, GuestStar, Composer, … */
    @SerialName("Type") val type: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
    @SerialName("ImageBlurHashes") val imageBlurHashes: Map<String, Map<String, String>> = emptyMap(),
)

/** `BaseItemDto.Chapters[]`. `ImagePath` is deliberately dropped — it is a server-local FS path. */
@Serializable
data class ChapterInfoDto(
    @SerialName("StartPositionTicks") val startPositionTicks: Long = 0,
    @SerialName("Name") val name: String? = null,
    @SerialName("ImageTag") val imageTag: String? = null,
    @SerialName("ImageDateModified") val imageDateModified: String? = null,
)

/** One trickplay width band. `Interval` is milliseconds between thumbnails. */
@Serializable
data class TrickplayInfoDto(
    @SerialName("Width") val width: Int = 0,
    @SerialName("Height") val height: Int = 0,
    @SerialName("TileWidth") val tileWidth: Int = 0,
    @SerialName("TileHeight") val tileHeight: Int = 0,
    @SerialName("ThumbnailCount") val thumbnailCount: Int = 0,
    @SerialName("Interval") val interval: Int = 0,
    @SerialName("Bandwidth") val bandwidth: Int = 0,
)

@Serializable
data class NameGuidPairDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
)

/** `ImageBlurHashes` is `{ imageType -> { tag -> hash } }`; look a hash up by the tag we already hold. */
fun Map<String, Map<String, String>>.blurHash(type: String, tag: String?): String? =
    if (tag == null) this[type]?.values?.firstOrNull() else this[type]?.get(tag)
```

### 2.3 `data/ImageUrlBuilder.kt` — new method signatures

```kotlin
    fun logo(itemId: String, tag: String?, maxWidth: Int = 480): String? =
        image(itemId, "Logo", tag, maxWidth)

    fun personPrimary(personId: String, tag: String?, maxWidth: Int = 240): String? =
        image(personId, "Primary", tag, maxWidth)

    /** `/Items/{id}/Images/Backdrop/{index}` — the indexed path form (§6 of the API surface). */
    fun backdropAtIndex(itemId: String, index: Int, tag: String?, maxWidth: Int = 1280): String? =
        imageAtIndex(itemId, "Backdrop", index, tag, maxWidth)

    /** `/Items/{id}/Images/Chapter/{index}` — `tag` comes from `ChapterInfoDto.imageTag`. */
    fun chapterImage(itemId: String, chapterIndex: Int, tag: String?, maxWidth: Int = 320): String? =
        imageAtIndex(itemId, "Chapter", chapterIndex, tag, maxWidth)

    fun imageAtIndex(itemId: String, type: String, index: Int, tag: String?, maxWidth: Int): String? {
        val server = serverUrlProvider() ?: return null
        val base = "$server/Items/$itemId/Images/$type/$index?maxWidth=$maxWidth&quality=90"
        return if (tag != null) "$base&tag=$tag" else base
    }

    // --- DTO-aware fallback chains. Each returns the first link that has BOTH an id and a tag. ---

    /** Episode → season/series thumb. Never pairs a parent tag with the child's id. */
    fun thumbWithSeriesFallback(item: BaseItemDto, maxWidth: Int = 640): String? =
        item.imageTags["Thumb"]?.let { thumb(item.id, it, maxWidth) }
            ?: item.parentThumbImageTag?.let { t -> item.parentThumbItemId?.let { thumb(it, t, maxWidth) } }
            ?: item.seriesThumbImageTag?.let { t -> item.seriesId?.let { thumb(it, t, maxWidth) } }

    /** Episode → season → series backdrop (§5.1 ambient wash, §5.2 stage). */
    fun backdropWithParentFallback(item: BaseItemDto, maxWidth: Int = 1280): String? =
        item.backdropImageTags.firstOrNull()?.let { backdrop(item.id, it, maxWidth) }
            ?: item.parentBackdropImageTags.firstOrNull()
                ?.let { t -> item.parentBackdropItemId?.let { backdrop(it, t, maxWidth) } }

    /** Own poster → series poster → parent poster. Replaces the three ad-hoc copies of this chain. */
    fun primaryWithParentFallback(item: BaseItemDto, maxWidth: Int = 320): String? =
        item.imageTags["Primary"]?.let { primary(item.id, it, maxWidth) }
            ?: item.seriesPrimaryImageTag?.let { t -> item.seriesId?.let { primary(it, t, maxWidth) } }
            ?: item.parentPrimaryImageTag
                ?.let { t -> item.parentPrimaryImageItemId?.let { primary(it, t, maxWidth) } }

    fun logoWithParentFallback(item: BaseItemDto, maxWidth: Int = 480): String? =
        item.imageTags["Logo"]?.let { logo(item.id, it, maxWidth) }
            ?: item.parentLogoImageTag?.let { t -> item.parentLogoItemId?.let { logo(it, t, maxWidth) } }
```

### 2.4 `data/repo/LibraryRepository.kt` — exact `fields=` / image params per call

```kotlin
private const val CARD_FIELDS = "Overview,PrimaryImageAspectRatio"
private const val DETAIL_FIELDS =
    "Overview,Genres,Taglines,Studios,People,Chapters,MediaStreams,MediaSources,PrimaryImageAspectRatio,Trickplay"
private const val CARD_IMAGE_TYPES = "Primary,Backdrop,Thumb,Logo"
private const val DETAIL_IMAGE_TYPES = "Primary,Backdrop,Thumb,Logo,Banner,Art"
```

| Call | Add / change |
|---|---|
| `userViews()` | `"enableImageTypes" to "Primary,Thumb,Backdrop"`, `"imageTypeLimit" to 1` |
| `resumeItems()` | `"fields"` → `CARD_FIELDS`; add `"enableImageTypes" to CARD_IMAGE_TYPES`, `"imageTypeLimit" to 1` |
| `nextUp()` | add `"fields" to CARD_FIELDS`, `"enableImageTypes" to CARD_IMAGE_TYPES`, `"imageTypeLimit" to 1` |
| `latest()` | add `"fields" to CARD_FIELDS`, `"enableImageTypes" to CARD_IMAGE_TYPES`, `"imageTypeLimit" to 1` |
| `items()` | `"fields"` → `"$CARD_FIELDS,Genres"`; `"enableImageTypes"` → `CARD_IMAGE_TYPES` |
| `item()` | **rewrite** (below) |
| `seasons()` | add `"fields" to CARD_FIELDS`, `"enableImageTypes" to "Primary,Thumb,Banner"`, `"imageTypeLimit" to 1` |
| `episodes()` | `"fields"` → `CARD_FIELDS`; add `"enableImageTypes" to "Primary,Thumb,Backdrop"`, `"imageTypeLimit" to 1` |
| `search()` | add `"enableUserData" to true` (**bug fix**), `"fields" to CARD_FIELDS`, `"enableImageTypes" to "Primary,Thumb"`, `"imageTypeLimit" to 1` |

`GET /Items/{itemId}` accepts **only** `userId` — there is no `fields` param on it (verified against
the spec). Route the detail fetch through `/Items?ids=` instead, which does:

```kotlin
    suspend fun item(itemId: String): ApiResult<BaseItemDto> {
        val userId = userIdOrFail() ?: return unauthorized()
        return client.get<QueryResult<BaseItemDto>>(
            "/Items",
            mapOf(
                "userId" to userId,
                "ids" to itemId,
                "fields" to DETAIL_FIELDS,
                "enableUserData" to true,
                "imageTypeLimit" to 3,
                "enableImageTypes" to DETAIL_IMAGE_TYPES,
            ),
        ).flatMap { result ->
            result.items.firstOrNull()
                ?.let { ApiResult.Success(it) }
                ?: ApiResult.Failure(ApiError.Http(404, "Item $itemId not found"))
        }
    }
```

(`flatMap` does not exist yet — add it next to `map` in `data/ApiResult.kt`, or inline a `when`.)

---

## 3. Implementation tasks

### DATA

| T | File | Work |
|---|---|---|
| D1 | `data/api/dto/ItemAssetDto.kt` **(new)** | §2.2 verbatim. All DTOs default-valued so `ignoreUnknownKeys` + missing arrays never throw. |
| D2 | `data/api/dto/BaseItemDto.kt` | Append §2.1. Nothing removed — the DTO stays trimmed to what a screen actually draws. |
| D3 | `data/ImageUrlBuilder.kt` | Add §2.3. `imageAtIndex` is the only new primitive; everything else composes it. |
| D4 | `data/repo/LibraryRepository.kt` | Apply the §2.4 table + the `item()` rewrite; hoist the four `const val`s to file scope. |
| D5 | `data/ApiResult.kt` | Add `inline fun <T, R> ApiResult<T>.flatMap(f: (T) -> ApiResult<R>): ApiResult<R>`. |
| D6 | `data/image/BlurHash.kt` **(new)** | Pure-Kotlin decoder: `fun decode(hash: String, width: Int, height: Int, punch: Float = 1f): IntArray?`. No `android.graphics` — returns ARGB ints so it unit-tests on the JVM. Returns `null` for malformed hashes (never throws). Decode at 32×32; the upscale is the blur. |
| D7 | `data/repo/PlaybackRepository.kt` | Add `val chapters: List<ChapterInfoDto>` and `val trickplay: Map<String, TrickplayInfoDto>` to `ResolvedPlayback`, defaulted to empty. `/PlaybackInfo` does not return them — `PlayerViewModel` fills them from its existing `libraryRepository.item()` call. |
| D8 | `app/src/test/.../ImageUrlBuilderTest.kt` | Cases: `logo`, `personPrimary`, indexed backdrop path (`…/Images/Backdrop/2?maxWidth=…&tag=t`), `chapterImage`; each fallback chain at every link **and** the negative case (parent tag present, parent id null → next link, never the child id). |
| D9 | `app/src/test/.../DtoDeserializationTest.kt` + `resources/fixtures/item_detail.json` **(new)** | Fixture carries `People`, `Chapters`, `ImageBlurHashes`, `Taglines`, `Trickplay`, `ParentBackdropImageTags`. Assert person role/tag, chapter ticks/tag, `blurHash("Primary", tag)`. |
| D10 | `app/src/test/.../LibraryRepositoryTest.kt` | Assert the *request URL* per call: `item()` hits `/Items` with `ids=` and `fields=…People…Chapters…`; `search()` sends `enableUserData=true`. `FakeJellyfin` already exposes the recorded `HttpRequestData`. |
| D11 | `app/src/test/.../BlurHashTest.kt` **(new)** | Known-hash → known corner pixel; malformed/short hash → `null`. |

### COMPONENTS + HOME

| T | File | Work |
|---|---|---|
| C1 | `ui/components/BlurHashPainter.kt` **(new)** | `@Composable fun rememberBlurHashPainter(hash: String?): Painter?` — `remember(hash)` around `BlurHash.decode(hash, 32, 32)` → `Bitmap.createBitmap(px, 32, 32, ARGB_8888).asImageBitmap()` → `BitmapPainter(…, filterQuality = FilterQuality.Low)`. |
| C2 | `ui/components/PosterImage.kt` | New `blurHash: String? = null` param. Layer order: `Ink100` → blurhash painter (if any) → `AsyncImage` (`crossfade(220)` unchanged) → veil. Keep the `1.dp #212125` inset hairline **only when there is no blurhash** (§5: the hairline says "deliberately empty", a blurhash says "loading"). Delete the KDoc sentence "No blurhash exists on the server". |
| C3 | `ui/components/MediaCard.kt` | `MediaCardUi` gains `blurHash: String?` and `aspect: Float?`; pass both through to `PosterImage`. Poster aspect uses `PrimaryImageAspectRatio` when present, else 2:3 — clamp to `0.5f..2.0f` so a bad server value cannot break the row's fixed height. |
| C4 | `ui/components/ItemMapping.kt` | Rewrite `toCardUi`: wide → `thumbWithSeriesFallback` → `backdropWithParentFallback` → `primaryWithParentFallback`; poster → `primaryWithParentFallback`. `backdropUrl` → `backdropWithParentFallback` (kills the untagged `images.backdrop(seriesId, null)` guess). Populate `blurHash` via `imageBlurHashes.blurHash(type, tag)` for whichever tag won. Extend `toSpecLine()` with `genres.take(2)` after the rating. |
| C5 | `ui/screens/home/HomeScreen.kt` | `AmbientHeader`: between title and spec line, render `MediaCardUi.tagline` when present — `bodyMedium @ Ash600`, 1 line, `Ellipsis`. Height stays `AmbientHeaderHeight` (94dp) so the rows never shift; drop the tagline line when absent rather than reserving space. |
| C6 | `ui/screens/home/HomeViewModel.kt` | No structural change — `toCardUi` carries the new fields. Verify `MediaCardUi` still has stable `equals` for the 250ms ambient debounce. |

### DETAIL

| T | File | Work |
|---|---|---|
| E1 | `ui/screens/detail/DetailViewModel.kt` | `DetailUiState` gains `logoUrl: String?`, `tagline: String?`, `genres: List<String>`, `cast: List<CastUi>`, `backdropBlurHash: String?`. Load: `logoUrl = imageUrlBuilder.logoWithParentFallback(item, 480)`, `tagline = item.taglines.firstOrNull()`, `cast = item.people.filter { it.type == "Actor" }.take(12).map { … }`. |
| E2 | `ui/screens/detail/DetailViewModel.kt` | Call `buildSpecRows(item, item.mediaStreams)` — the AUDIO/SUBS/CODEC branches are already written and currently unreachable. Delete the stale KDoc paragraph that says `MediaStreams` is unavailable. Add `CriticRating` (`SCORE` row already takes `CommunityRating`; render critic as `CRITIC 88%`) and, for Series, a `YEARS 2016–2022` row from `ProductionYear`+`EndDate`/`Status`. |
| E3 | `ui/screens/detail/DetailScreen.kt` | **Logo hero (§5.2)**: when `logoUrl != null`, replace the `displayLarge` title in `StaggeredBlock(index = 0)` with `AsyncImage(logoUrl, contentScale = ContentScale.Fit, alignment = CenterStart)` in a `Modifier.heightIn(max = 120.dp).widthIn(max = Dimens.BodyMaxWidth)` box, `contentDescription = item.name`. Fall back to the text title on null **and** on Coil error — a missing logo must never leave the stage titleless. |
| E4 | `ui/screens/detail/DetailScreen.kt` | **Tagline**: new `StaggeredBlock` between title and spec line — `bodyLarge`, `Paper @0.55`, `maxLines = 2`, 12dp above the spec line. Stagger indices shift; §6 caps the stagger at 5 blocks, so fold tagline into the title block's tween rather than adding a 6th delay step. |
| E5 | `ui/screens/detail/DetailScreen.kt` | **Genres**: append to the existing spec line via `specLine(year, runtime, rating, genres.take(3).joinToString(" · "))` — no chips, no pills (§4.2 forbids containers here). |
| E6 | `ui/screens/detail/DetailScreen.kt` | **Cast row (§5.1 row-header device)**: `CAST` eyebrow + hairline + zero-padded folio, then a `LazyRow` of 120×120 tiles at `Dimens.ItemSpacing`. Tile = `PosterImage(aspect = 1f, blurHash = person.blurHash)` with `personPrimary(person.id, person.primaryImageTag, 240)`; name `titleSmall @0.55→@1.0`, role `bodySmall @ Ash600`, both 1 line in a fixed 44dp label block. Focus is the §4.1 four-signal treatment at scale **1.04** (grid rule — a row of squares collides at 1.08). Non-clickable for now: `focusable()` without `clickable` until a Person screen exists. |
| E7 | `ui/screens/detail/DetailScreen.kt` | **Stage blurhash**: pass `item.imageBlurHashes.blurHash("Backdrop", tag)` under the backdrop `AsyncImage` so the 1920px stage fades up from colour, not from black. |
| E8 | `ui/screens/detail/DetailViewModel.kt` | `toEpisodeUi` → `thumbWithSeriesFallback(episode, 640)`, then `primaryWithParentFallback`; today it pairs the series/parent tag with the episode id and silently 404s. |

### PLAYER

| T | File | Work |
|---|---|---|
| P1 | `ui/screens/player/PlayerViewModel.kt` | Keep the existing `libraryRepository.item(itemId)` call; also read `chapters` off it. `PlayerUiState` gains `chapters: List<ChapterMark>` where `ChapterMark(positionMs: Long, name: String?)`, built with `Ticks.toMs(startPositionTicks)`. Drop chapters whose position ≥ duration. |
| P2 | `ui/screens/player/PlayerOsd.kt` | New `chapters: List<ChapterMark> = emptyList()` param, threaded into `BottomPlate` → `SeekBar`. |
| P3 | `ui/screens/player/PlayerOsd.kt` — `SeekBar` | **Chapter markers (§5.3, hairline not HUD)**: for each chapter with `positionMs > 0`, a `1.dp` wide × `10.dp` tall bar at `laneWidth * (positionMs / duration)`, centred on the lane, `Lit @ Alpha.TextDisabled` (0.38). Draw **above** the track and buffered layer but **below** the played fill and the scrubber, so the tungsten fill swallows passed chapters — the markers read as "what's ahead". No labels on the bar, ever. |
| P4 | `ui/screens/player/PlayerOsd.kt` — `TopPlate` | While `seeking`, append the current chapter name to the spec line as `… · CHAPTER NAME` (`labelMedium @0.55`, uppercase, 1 line). Current chapter = last chapter with `positionMs <= positionMs`. No chapter name when not seeking — §5.3's spec line is identity, not telemetry. |
| P5 | `ui/screens/player/PlayerScreen.kt` | Optional, cheap: D-pad **UP/DOWN** while the OSD is visible jumps to prev/next chapter (`seekTo`). Gate behind `chapters.size >= 2` so single-chapter files keep default focus behaviour. |
| P6 | — | **Trickplay: follow-up, not this wave.** See §4. |

### LIBRARY + SEARCH

| T | File | Work |
|---|---|---|
| L1 | `ui/screens/library/LibraryGrid.kt` | `GridTileUi` gains `blurHash: String?` and `aspect: Float?`; `PosterGridTile` passes both to `PosterImage`. Tile box height stays `Dimens.CardHeight` — a non-2:3 poster letterboxes inside the fixed tile rather than reflowing the grid (§5.4). |
| L2 | `ui/screens/library/LibraryGrid.kt` — `toGridTile` | Replace the hand-rolled `imageTags["Primary"] ?: seriesPrimaryImageTag ?: parentThumbImageTag` chain with `images.primaryWithParentFallback(this, POSTER_IMAGE_WIDTH)`; fill `blurHash` from `imageBlurHashes`. |
| L3 | `ui/screens/search/SearchViewModel.kt` | No code change beyond D4's `enableUserData=true` — but **verify** watched checks and progress bars now appear on Search tiles; that is the regression this fixes. |
| L4 | `ui/screens/search/SearchScreen.kt` | For `Type == "Episode"` results, prefer `thumbWithSeriesFallback` at 320 over the poster so an episode hit is recognisable; keep the 2:3 tile and `ContentScale.Crop`. |
| L5 | `docs/ui-design.md` §5 | One-line amendment: replace "No blurhash exists, so the placeholder is a flat `Ink100` rect…" with "Blurhash **does** exist (`ImageBlurHashes`); the flat `Ink100` rect + `1.dp #212125` hairline is the fallback when a hash is absent. `crossfade(220)`; never a shimmer." |

---

## 4. Follow-ups (documented, not scheduled)

**Trickplay scrubbing** — deliberately deferred. The manifest is
`Trickplay: { mediaSourceId → { widthString → TrickplayInfoDto } }`; tiles come from
`GET /Videos/{itemId}/Trickplay/{width}/{index}.jpg?mediaSourceId=…`. Math, pinned so the later
task is mechanical:

```
perTile      = tileWidth * tileHeight
thumbIndex   = floor(positionMs / interval)          // interval is ms
tileIndex    = thumbIndex / perTile                  // → {index}.jpg
withinTile   = thumbIndex % perTile
srcX         = (withinTile % tileWidth)  * width
srcY         = (withinTile / tileWidth)  * height
```

Three reasons it is not in this wave: (a) it needs sub-rect cropping of a remote JPEG, which Coil
does not do declaratively — it means a custom `Painter` over a decoded `ImageBitmap`;
(b) `docs/ui-design.md` §5.3 has **no** design for a scrub preview and explicitly bans panels and
cards over video, so the surface has to be designed before it is built; (c) chapter markers (P1–P4)
deliver most of the navigational value at a fraction of the cost. Requires `fields=Trickplay`,
which D4 already requests, so the data will be sitting in the DTO when the design lands.

**Also deferred:** `ScreenshotImageTags` (no surface), `OriginalTitle` (needs a §3 typographic
rule for the secondary title), `Studios` (a logo row needs `/Studios/{name}/Images/Primary` and a
design), Banner/Art/Disc/Box/Menu images (no idiom in §5 — explicitly won't-fix, not oversight).

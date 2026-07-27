package com.maik205.shoumeiplayer.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.api.dto.MediaStreamDto
import com.maik205.shoumeiplayer.data.api.dto.blurHash
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.util.Ticks
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * §5.2 — one row of the right-hand spec block: sentence-case label on the left, value on the
 * right. [mono] marks the clock-like values (durations) that §3.1 reserves for the mono family.
 */
data class SpecRow(val label: String, val value: String, val mono: Boolean = false)

/** §5.2 — an episode thumb: `S1:E04` badge, title + runtime below the card. */
data class EpisodeUi(
    val id: String,
    val badge: String,
    val title: String,
    val runtime: String?,
    val imageUrl: String?,
    val watched: Boolean,
    val progressFraction: Float?,
)

/**
 * §5.2 — one 120×120 cast tile: portrait, name, role. Non-clickable until a Person screen
 * exists, so it carries no navigation id beyond the key.
 */
data class CastUi(
    val id: String,
    val name: String,
    val role: String?,
    val imageUrl: String?,
    val blurHash: String?,
)

data class DetailUiState(
    val loading: Boolean = true,
    val item: BaseItemDto? = null,
    val backdropUrl: String? = null,
    val posterUrl: String? = null,
    /** §5 — the stage fades up from colour, not from black, when the server ships a hash. */
    val backdropBlurHash: String? = null,
    val posterBlurHash: String? = null,
    /** §5.2 — logo art replaces the `displayLarge` title when the item has one. */
    val logoUrl: String? = null,
    val tagline: String? = null,
    val genres: List<String> = emptyList(),
    val cast: List<CastUi> = emptyList(),
    val seasons: List<BaseItemDto> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<EpisodeUi> = emptyList(),
    /** §5.2 — hidden on Series; 4 to 5 rows drawn from the item's own metadata. */
    val specRows: List<SpecRow> = emptyList(),
    val error: String? = null,
) {
    /** §5.2 — the tungsten resume bar renders only when there is a position to resume from. */
    val resumeFraction: Float
        get() {
            val position = item?.userData?.playbackPositionTicks ?: 0L
            val total = item?.runTimeTicks ?: 0L
            return if (position > 0 && total > 0) (position.toFloat() / total).coerceIn(0f, 1f) else 0f
        }
}

/**
 * Loads a single item (movie or series) plus, for series, its seasons and the
 * episodes of the selected season.
 */
class DetailViewModel(
    private val libraryRepository: LibraryRepository,
    private val imageUrlBuilder: ImageUrlBuilder,
    private val itemId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DetailUiState())
    val uiState: StateFlow<DetailUiState> = _uiState.asStateFlow()

    /** Resume position (ticks) for the top-level item, 0 if never played. */
    val resumePositionTicks: Long
        get() = _uiState.value.item?.userData?.playbackPositionTicks ?: 0

    /** Resume ticks per episode id, tracked alongside the display-mapped [DetailUiState.episodes]. */
    private var episodeResumeTicks: Map<String, Long> = emptyMap()

    init {
        load()
    }

    fun retry() {
        load()
    }

    /** Resume position (ticks) for an episode card previously loaded by [load]/[selectSeason]. */
    fun resumeTicksFor(episodeId: String): Long = episodeResumeTicks[episodeId] ?: 0

    fun selectSeason(seasonId: String) {
        if (seasonId == _uiState.value.selectedSeasonId) return
        val seriesId = _uiState.value.item?.id ?: return
        _uiState.update { it.copy(selectedSeasonId = seasonId) }
        loadEpisodes(seriesId, seasonId)
    }

    private fun load() {
        _uiState.update { DetailUiState(loading = true) }
        viewModelScope.launch {
            when (val result = libraryRepository.item(itemId)) {
                is ApiResult.Success -> {
                    val item = result.data
                    val backdropTag = item.backdropImageTags.firstOrNull()
                    val posterTag = item.imageTags["Primary"]
                    _uiState.update {
                        it.copy(
                            loading = false,
                            item = item,
                            backdropUrl = imageUrlBuilder.backdropWithParentFallback(
                                item,
                                DETAIL_BACKDROP_WIDTH,
                            ),
                            posterUrl = imageUrlBuilder.primaryWithParentFallback(
                                item,
                                DETAIL_POSTER_WIDTH,
                            ),
                            // Parent-fallback art belongs to another item, whose hashes we never
                            // fetched — only look a hash up when the item's *own* tag won.
                            backdropBlurHash = backdropTag
                                ?.let { tag -> item.imageBlurHashes.blurHash("Backdrop", tag) },
                            posterBlurHash = posterTag
                                ?.let { tag -> item.imageBlurHashes.blurHash("Primary", tag) },
                            logoUrl = imageUrlBuilder.logoWithParentFallback(item, DETAIL_LOGO_WIDTH),
                            tagline = item.taglines.firstOrNull()?.takeIf { line -> line.isNotBlank() },
                            genres = item.genres.filter { genre -> genre.isNotBlank() },
                            cast = item.toCastUi(imageUrlBuilder),
                            specRows = if (item.type == "Series") {
                                emptyList()
                            } else {
                                buildSpecRows(item, item.mediaStreams)
                            },
                            error = null,
                        )
                    }
                    if (item.type == "Series") {
                        loadSeasons(item.id)
                    }
                }

                is ApiResult.Failure -> {
                    _uiState.update { it.copy(loading = false, error = result.error.displayMessage) }
                }
            }
        }
    }

    private fun loadSeasons(seriesId: String) {
        viewModelScope.launch {
            when (val result = libraryRepository.seasons(seriesId)) {
                is ApiResult.Success -> {
                    val seasons = result.data
                    val firstSeasonId = seasons.firstOrNull()?.id
                    _uiState.update { it.copy(seasons = seasons, selectedSeasonId = firstSeasonId) }
                    if (firstSeasonId != null) {
                        loadEpisodes(seriesId, firstSeasonId)
                    }
                }

                is ApiResult.Failure -> {
                    // Item itself loaded fine; leave seasons empty rather than erroring the whole screen.
                }
            }
        }
    }

    private fun loadEpisodes(seriesId: String, seasonId: String?) {
        viewModelScope.launch {
            when (val result = libraryRepository.episodes(seriesId, seasonId)) {
                is ApiResult.Success -> {
                    val raw = result.data
                    episodeResumeTicks = raw.associate { it.id to (it.userData?.playbackPositionTicks ?: 0) }
                    val episodes = raw.map { it.toEpisodeUi(imageUrlBuilder) }
                    _uiState.update { it.copy(episodes = episodes) }
                }

                is ApiResult.Failure -> {
                    // Keep whatever episodes were already shown.
                }
            }
        }
    }
}

/** Coil (§5): Detail backdrop 1920; poster fallback 220×330dp @2x; logo 480; episode thumb 640. */
private const val DETAIL_BACKDROP_WIDTH = 1920
private const val DETAIL_POSTER_WIDTH = 440
private const val DETAIL_LOGO_WIDTH = 480
private const val EPISODE_THUMB_WIDTH = 640
private const val CAST_IMAGE_WIDTH = 240

/** §5.2 — the cast row is people you can see: actors and guest stars, portraits first, capped at 12. */
private const val CAST_LIMIT = 12

internal fun BaseItemDto.toCastUi(images: ImageUrlBuilder): List<CastUi> =
    people
        .asSequence()
        .filter { it.type == "Actor" || it.type == "GuestStar" }
        .filter { !it.name.isNullOrBlank() }
        .take(CAST_LIMIT)
        .map { person ->
            CastUi(
                id = person.id,
                name = person.name.orEmpty(),
                role = person.role?.takeIf { it.isNotBlank() },
                imageUrl = person.primaryImageTag
                    ?.let { images.personPrimary(person.id, it, CAST_IMAGE_WIDTH) },
                blurHash = person.imageBlurHashes.blurHash("Primary", person.primaryImageTag),
            )
        }
        .toList()

private fun BaseItemDto.toEpisodeUi(images: ImageUrlBuilder): EpisodeUi =
    EpisodeUi(
        id = id,
        badge = "S${parentIndexNumber ?: 0}:E${(indexNumber ?: 0).toString().padStart(2, '0')}",
        title = name.orEmpty(),
        runtime = runTimeTicks?.let { formatRuntime(it) },
        // Episode thumb → season/series thumb → poster chain. Never pairs a parent tag with this id.
        imageUrl = images.thumbWithSeriesFallback(this, EPISODE_THUMB_WIDTH)
            ?: images.primaryWithParentFallback(this, EPISODE_THUMB_WIDTH),
        watched = userData?.played == true,
        progressFraction = userData?.playedPercentage
            ?.toFloat()
            ?.takeIf { it > 0f && it < 100f }
            ?.div(100f),
    )

/**
 * §5.2 — the spec block, built from data the detail fetch already carries: `Runtime 2h 44m`,
 * `Rating TV-MA`, `Audio 5.1 EAC3`, `Subtitles 3`, `Codec HEVC`, `Score 8.4`. Capped at five rows.
 *
 * [streams] arrives from `fields=MediaStreams`. One rating scale only: the critic percentage is
 * dropped, and the year lives in the metadata line rather than repeating itself here.
 */
internal fun buildSpecRows(
    item: BaseItemDto,
    streams: List<MediaStreamDto> = emptyList(),
): List<SpecRow> {
    val rows = mutableListOf<SpecRow>()
    item.runTimeTicks?.takeIf { it > 0 }?.let {
        rows += SpecRow("Runtime", formatRuntime(it), mono = true)
    }
    item.officialRating?.takeIf { it.isNotBlank() }?.let { rows += SpecRow("Rating", it) }

    val video = streams.firstOrNull { it.type == "Video" }
    val audio = streams.firstOrNull { it.type == "Audio" && it.isDefault }
        ?: streams.firstOrNull { it.type == "Audio" }
    val subtitleCount = streams.count { it.type == "Subtitle" }

    if (audio != null) {
        // Codecs are acronyms as the server ships them, not a tracked-out label.
        val parts = listOfNotNull(channelLayout(audio.channels), audio.codec?.uppercase())
        if (parts.isNotEmpty()) rows += SpecRow("Audio", parts.joinToString(" "))
    }
    if (subtitleCount > 0) {
        rows += SpecRow("Subtitles", subtitleCount.toString())
    }
    video?.codec?.uppercase()?.let { rows += SpecRow("Codec", it) }

    if (rows.size < SPEC_ROW_LIMIT) {
        item.communityRating?.let { rows += SpecRow("Score", "%.1f".format(it)) }
    }
    return rows.take(SPEC_ROW_LIMIT)
}

/** §5.2 — four to five rows; a sixth turns the block into a table. */
private const val SPEC_ROW_LIMIT = 5

/**
 * §5.2 — `2016`, `2016-2022` (finished run) or `2016-` (still running). Falls back to the bare
 * production year whenever `EndDate` / `Status` say nothing useful. Ranges take a hyphen (§1).
 */
internal fun yearRange(item: BaseItemDto): String? {
    val start = item.productionYear ?: return null
    val endYear = item.endDate?.take(4)?.toIntOrNull()
    return when {
        endYear != null && endYear > start -> "$start-$endYear"
        endYear != null -> start.toString()
        item.status.equals("Continuing", ignoreCase = true) -> "$start-"
        else -> start.toString()
    }
}

/** §5.2 — Series only: `Ended` when the run is over and no `EndDate` gave us a closing year. */
internal fun runStatus(item: BaseItemDto): String? = item.status
    ?.takeIf { it.equals("Ended", ignoreCase = true) || it.equals("Cancelled", ignoreCase = true) }
    ?.takeIf { item.endDate?.take(4)?.toIntOrNull() == null }
    ?.replaceFirstChar { it.uppercase() }

/**
 * §3.2 — the Detail metadata line as columns, not a dot chain: `2019    TV-MA`, with the runtime
 * rendered beside them in mono by the screen. Genres are a list of their own (§5.2) and never
 * nest inside this one.
 */
internal fun metadataFields(
    years: String?,
    status: String? = null,
    rating: String? = null,
): List<String> = listOfNotNull(
    years,
    status,
    rating?.takeIf { it.isNotBlank() },
)

/** §5.2 — genres get their own line under the overview, joined by a comma. */
internal fun genreLine(genres: List<String>, limit: Int = 4): String =
    genres.filter { it.isNotBlank() }.take(limit).joinToString(", ")

/** §5.2 — `2h 44m`, sentence case, rendered in mono as a duration (§3.1). */
internal fun formatRuntime(ticks: Long): String {
    val totalMinutes = Ticks.toMs(ticks) / 60_000
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun channelLayout(channels: Int?): String? = when (channels) {
    null -> null
    1 -> "Mono"
    2 -> "Stereo"
    6 -> "5.1"
    8 -> "7.1"
    else -> "$channels ch"
}

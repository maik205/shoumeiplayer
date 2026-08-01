package com.maik205.shoumeiplayer.ui.television.screens.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.domain.model.DetailItem
import com.maik205.shoumeiplayer.domain.model.DetailPerson
import com.maik205.shoumeiplayer.domain.model.PlayableTarget
import com.maik205.shoumeiplayer.domain.repository.MediaDetailsRepository
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.ui.i18n.UiText
import com.maik205.shoumeiplayer.ui.i18n.toUiText
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
enum class DetailKind {
    Film,
    Series,
    Episode,
    Album,
    Artist,
    Playlist,
    AudioBook,
    Collection,
    Person,
    Live,
    Generic,
}

@Immutable
enum class DetailSection {
    Playable,
    Seasons,
    Episodes,
    Tracks,
    Releases,
    Related,
    Credits,
}

@Immutable
data class PersonUi(
    val id: String,
    val name: String,
    val role: String?,
    val type: String?,
    val imageUrl: String?,
)

@Immutable
data class TelevisionDetailState(
    val loading: Boolean = true,
    val kind: DetailKind = DetailKind.Generic,
    val item: DetailItem? = null,
    val hero: MediaItemUi? = null,
    val playbackItem: DetailItem? = null,
    val nextUp: MediaItemUi? = null,
    val playableItemId: String? = null,
    val playableResumeTicks: Long = 0,
    val seasons: List<MediaItemUi> = emptyList(),
    val selectedSeasonId: String? = null,
    val episodes: List<MediaItemUi> = emptyList(),
    val tracks: List<MediaItemUi> = emptyList(),
    val releases: List<MediaItemUi> = emptyList(),
    val related: List<MediaItemUi> = emptyList(),
    val people: List<PersonUi> = emptyList(),
    val credits: List<MediaItemUi> = emptyList(),
    val error: UiText? = null,
    val actionError: UiText? = null,
    val sectionErrors: Map<DetailSection, UiText> = emptyMap(),
    val seasonLoading: Boolean = false,
    val seasonError: UiText? = null,
    val seasonErrorId: String? = null,
) {
    val isPlayable: Boolean
        get() = playableItemId != null
}

private enum class DetailAction {
    Favorite,
    Played,
}

class TelevisionDetailViewModel(
    private val repository: MediaDetailsRepository,
    private val itemId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(TelevisionDetailState())
    val state: StateFlow<TelevisionDetailState> = _state.asStateFlow()
    private var reloadJob: Job? = null
    private var seasonJob: Job? = null
    private var lastAction: DetailAction? = null

    private data class SectionResult<T>(
        val data: T?,
        val error: UiText?,
    )

    init {
        reload()
    }

    fun reload() {
        reloadJob?.cancel()
        seasonJob?.cancel()
        lastAction = null
        reloadJob = viewModelScope.launch {
            _state.update {
                it.copy(
                    loading = true,
                    error = null,
                    actionError = null,
                    sectionErrors = emptyMap(),
                    seasonLoading = false,
                    seasonError = null,
                    seasonErrorId = null,
                )
            }
            try {
                when (val result = repository.item(itemId)) {
                    is ApiResult.Failure -> _state.update {
                        it.copy(loading = false, error = result.error.toUiText())
                    }

                    is ApiResult.Success -> loadDetail(result.data)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = UiText.Resource(R.string.tv_detail_load_failed),
                    )
                }
            }
        }
    }

    fun selectSeason(seasonId: String) {
        val item = _state.value.item ?: return
        val seriesId = when (_state.value.kind) {
            DetailKind.Series -> item.id
            DetailKind.Episode -> item.seriesId
            else -> null
        } ?: return
        if (_state.value.selectedSeasonId == seasonId) return
        val previousSeasonId = _state.value.selectedSeasonId
        _state.update {
            it.copy(
                selectedSeasonId = seasonId,
                seasonLoading = true,
                seasonError = null,
                seasonErrorId = null,
            )
        }
        seasonJob?.cancel()
        seasonJob = viewModelScope.launch {
            try {
                when (val result = repository.episodes(seriesId, seasonId)) {
                    is ApiResult.Failure -> _state.update {
                        it.copy(
                            selectedSeasonId = previousSeasonId,
                            seasonLoading = false,
                            seasonError = result.error.toUiText(),
                            seasonErrorId = seasonId,
                        )
                    }
                    is ApiResult.Success -> {
                        _state.update {
                            it.copy(
                                selectedSeasonId = seasonId,
                                episodes = result.data,
                                seasonLoading = false,
                                seasonError = null,
                                seasonErrorId = null,
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        selectedSeasonId = previousSeasonId,
                        seasonLoading = false,
                        seasonError = UiText.Resource(R.string.tv_detail_section_failed),
                        seasonErrorId = seasonId,
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        val current = _state.value.item ?: return
        val favorite = current.favorite
        lastAction = DetailAction.Favorite
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                when (val result = repository.setFavorite(current, !favorite)) {
                    is ApiResult.Failure -> _state.update {
                        it.copy(actionError = result.error.toUiText())
                    }

                    is ApiResult.Success -> {
                        val updated = result.data
                        lastAction = null
                        _state.update {
                            it.copy(
                                item = updated,
                                hero = updated.media,
                                playbackItem = if (it.playbackItem?.id == updated.id) updated else it.playbackItem,
                                actionError = null,
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update { it.copy(actionError = UiText.Resource(R.string.tv_detail_action_failed)) }
            }
        }
    }

    fun togglePlayed() {
        val current = _state.value.item ?: return
        val played = current.played
        lastAction = DetailAction.Played
        viewModelScope.launch {
            _state.update { it.copy(actionError = null) }
            try {
                when (val result = repository.setPlayed(current, !played)) {
                    is ApiResult.Failure -> _state.update {
                        it.copy(actionError = result.error.toUiText())
                    }

                    is ApiResult.Success -> {
                        val updated = result.data
                        lastAction = null
                        _state.update {
                            it.copy(
                                item = updated,
                                hero = updated.media,
                                playbackItem = if (it.playbackItem?.id == updated.id) updated else it.playbackItem,
                                actionError = null,
                            )
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Throwable) {
                _state.update { it.copy(actionError = UiText.Resource(R.string.tv_detail_action_failed)) }
            }
        }
    }

    fun retryLastAction() {
        when (lastAction) {
            DetailAction.Favorite -> toggleFavorite()
            DetailAction.Played -> togglePlayed()
            null -> reload()
        }
    }

    private suspend fun loadDetail(item: DetailItem) = coroutineScope {
        val kind = kindFor(item.type)
        val sectionErrors = linkedMapOf<DetailSection, UiText>()

        fun <T> record(section: DetailSection, result: SectionResult<T>) {
            result.error?.let { sectionErrors[section] = it }
        }

        val seriesPlayable = if (kind == DetailKind.Series) {
            async { safeRequest { repository.resolvePlayableTarget(item) } }
        } else {
            null
        }
        val related = if (kind in setOf(DetailKind.Person, DetailKind.Artist, DetailKind.Live)) {
            null
        } else {
            async { safeRequest { repository.similar(item.id, 18) } }
        }
        val seriesId = when (kind) {
            DetailKind.Series -> item.id
            DetailKind.Episode -> item.seriesId
            else -> null
        }
        val seasons = if (seriesId != null) {
            async { safeRequest { repository.seasons(seriesId) } }
        } else {
            null
        }
        val children = when (kind) {
            DetailKind.Album -> async { safeRequest { repository.albumTracks(item.id) } }
            DetailKind.Playlist -> async { safeRequest { repository.playlistItems(item.id) } }
            DetailKind.AudioBook -> async { safeRequest { repository.audioBookItems(item.id) } }
            DetailKind.Collection -> async { safeRequest { repository.collectionItems(item.id) } }
            else -> null
        }
        val artistReleases = if (kind == DetailKind.Artist) {
            async { safeRequest { repository.artistAlbums(item.id) } }
        } else {
            null
        }
        val artistTracks = if (kind == DetailKind.Artist) {
            async { safeRequest { repository.artistSongs(item.id) } }
        } else {
            null
        }
        val personCredits = if (kind == DetailKind.Person) {
            async { safeRequest { repository.personCredits(item.id) } }
        } else {
            null
        }

        val playableResult = seriesPlayable?.await()
        playableResult?.let { record(DetailSection.Playable, it) }
        val seasonResult = seasons?.await()
        seasonResult?.let { record(DetailSection.Seasons, it) }
        val childResult = children?.await()
        childResult?.let { record(DetailSection.Tracks, it) }
        val releaseResult = artistReleases?.await()
        releaseResult?.let { record(DetailSection.Releases, it) }
        val artistTrackResult = artistTracks?.await()
        artistTrackResult?.let { record(DetailSection.Tracks, it) }
        val relatedResult = related?.await()
        relatedResult?.let { record(DetailSection.Related, it) }
        val personCreditsResult = personCredits?.await()
        personCreditsResult?.let { record(DetailSection.Credits, it) }

        val seasonItems = seasonResult?.data.orEmpty()
        val childItems = childResult?.data.orEmpty()
        val releaseItems = releaseResult?.data.orEmpty()
        val artistTrackItems = artistTrackResult?.data.orEmpty()
        val playable = when (kind) {
            DetailKind.Series -> playableResult?.data
            DetailKind.Artist -> choosePlayable(artistTrackItems)?.toPlayableTarget()
            DetailKind.Collection, DetailKind.Person -> null
            DetailKind.Album, DetailKind.Playlist, DetailKind.AudioBook ->
                choosePlayable(childItems)?.toPlayableTarget()

            DetailKind.Live -> {
                val targetId = if (item.type.isLiveProgramType()) {
                    item.channelId?.takeIf(String::isNotBlank)
                } else {
                    item.id
                }
                targetId?.let {
                    PlayableTarget(
                        itemId = it,
                        startPositionTicks = item.resumeTicks,
                    )
                }
            }

            else -> PlayableTarget(
                itemId = item.id,
                startPositionTicks = item.resumeTicks,
            )
        }
        val nextUpResult = if (kind == DetailKind.Series && playable != null) {
            safeRequest { repository.item(playable.itemId) }
        } else {
            null
        }
        nextUpResult?.let { record(DetailSection.Playable, it) }
        val seriesNextUp = nextUpResult?.data
        val selectedSeason = when (kind) {
            DetailKind.Series -> {
                seriesNextUp?.seasonId
                    ?.takeIf { nextSeasonId ->
                        seasonItems.isEmpty() || seasonItems.any { it.id == nextSeasonId }
                    }
                    ?: seasonItems.firstOrNull { it.episodeNumber != 0 }?.id
                    ?: seasonItems.firstOrNull()?.id
            }

            DetailKind.Episode -> {
                item.seasonId
                    ?.takeIf { episodeSeasonId ->
                        seasonItems.isEmpty() || seasonItems.any { it.id == episodeSeasonId }
                    }
                    ?: seasonItems.firstOrNull { it.episodeNumber == item.parentIndexNumber }?.id
                    ?: seasonItems.firstOrNull()?.id
            }

            else -> null
        }
        val episodeResult = if (seriesId != null && selectedSeason != null) {
            safeRequest { repository.episodes(seriesId, selectedSeason) }
        } else {
            null
        }
        episodeResult?.let { record(DetailSection.Episodes, it) }

        _state.value = TelevisionDetailState(
            loading = false,
            kind = kind,
            item = item,
            hero = item.media,
            playbackItem = seriesNextUp ?: item,
            nextUp = seriesNextUp?.media,
            playableItemId = playable?.itemId,
            playableResumeTicks = playable?.startPositionTicks ?: 0,
            seasons = seasonItems,
            selectedSeasonId = selectedSeason,
            episodes = episodeResult?.data.orEmpty(),
            tracks = childItems + artistTrackItems,
            releases = releaseItems,
            related = relatedResult?.data.orEmpty(),
            people = item.people.map(::personUi),
            credits = personCreditsResult?.data.orEmpty(),
            sectionErrors = sectionErrors,
        )
    }

    private suspend fun <T> safeRequest(
        request: suspend () -> ApiResult<T>,
    ): SectionResult<T> = try {
        when (val result = request()) {
            is ApiResult.Success -> SectionResult(data = result.data, error = null)
            is ApiResult.Failure -> SectionResult(data = null, error = result.error.toUiText())
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Throwable) {
        SectionResult(
            data = null,
            error = UiText.Resource(R.string.tv_detail_section_failed),
        )
    }

    private fun personUi(person: DetailPerson): PersonUi = PersonUi(
        id = person.id,
        name = person.name.orEmpty(),
        role = person.role,
        type = person.type,
        imageUrl = person.imageUrl,
    )

    private fun choosePlayable(items: List<MediaItemUi>): MediaItemUi? =
        items.firstOrNull { it.progress != null }
            ?: items.firstOrNull { !it.watched }
            ?: items.firstOrNull()

    private fun MediaItemUi.toPlayableTarget(): PlayableTarget =
        PlayableTarget(itemId = id, startPositionTicks = resumeTicks)

}

internal fun kindFor(type: String?): DetailKind = when {
    type.isLiveProgramType() -> DetailKind.Live
    else -> when (type) {
        "Movie", "Video", "Trailer" -> DetailKind.Film
        "Series", "Season" -> DetailKind.Series
        "Episode" -> DetailKind.Episode
        "MusicAlbum" -> DetailKind.Album
        "MusicArtist" -> DetailKind.Artist
        "Playlist" -> DetailKind.Playlist
        "AudioBook", "Book" -> DetailKind.AudioBook
        "BoxSet", "CollectionFolder" -> DetailKind.Collection
        "Person" -> DetailKind.Person
        "LiveTvChannel", "Recording", "TvChannel" -> DetailKind.Live
        else -> DetailKind.Generic
    }
}

private fun String?.isLiveProgramType(): Boolean =
    equals("Program", ignoreCase = true) ||
        equals("LiveTvProgram", ignoreCase = true) ||
        equals("TvProgram", ignoreCase = true)

private fun childTypesFor(kind: DetailKind): List<String> = when (kind) {
    DetailKind.Album, DetailKind.Playlist -> listOf("Audio")
    DetailKind.AudioBook -> listOf("AudioBook", "Audio")
    DetailKind.Collection -> listOf("Movie", "Series", "Video", "MusicAlbum", "AudioBook")
    else -> emptyList()
}

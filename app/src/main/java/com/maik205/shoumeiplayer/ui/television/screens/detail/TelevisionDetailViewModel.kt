package com.maik205.shoumeiplayer.ui.television.screens.detail

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.domain.model.DetailItem
import com.maik205.shoumeiplayer.domain.model.DetailPerson
import com.maik205.shoumeiplayer.domain.model.PlayableTarget
import com.maik205.shoumeiplayer.domain.repository.MediaDetailsRepository
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val error: String? = null,
) {
    val isPlayable: Boolean
        get() = playableItemId != null
}

class TelevisionDetailViewModel(
    private val repository: MediaDetailsRepository,
    private val itemId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(TelevisionDetailState())
    val state: StateFlow<TelevisionDetailState> = _state.asStateFlow()
    private var reloadJob: Job? = null
    private var seasonJob: Job? = null

    init {
        reload()
    }

    fun reload() {
        reloadJob?.cancel()
        seasonJob?.cancel()
        reloadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            when (val result = repository.item(itemId)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(loading = false, error = result.error.displayMessage)
                }

                is ApiResult.Success -> loadDetail(result.data)
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
        _state.update { it.copy(selectedSeasonId = seasonId, error = null) }
        seasonJob?.cancel()
        seasonJob = viewModelScope.launch {
            when (val result = repository.episodes(seriesId, seasonId)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(
                        selectedSeasonId = previousSeasonId,
                        error = result.error.displayMessage,
                    )
                }
                is ApiResult.Success -> {
                    _state.update {
                        it.copy(
                            selectedSeasonId = seasonId,
                            episodes = result.data,
                        )
                    }
                }
            }
        }
    }

    fun toggleFavorite() {
        val current = _state.value.item ?: return
        val favorite = current.favorite
        viewModelScope.launch {
            when (val result = repository.setFavorite(current, !favorite)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(error = result.error.displayMessage)
                }

                is ApiResult.Success -> {
                    val updated = result.data
                    _state.update {
                        it.copy(
                            item = updated,
                            hero = updated.media,
                            playbackItem = if (it.playbackItem?.id == updated.id) updated else it.playbackItem,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    fun togglePlayed() {
        val current = _state.value.item ?: return
        val played = current.played
        viewModelScope.launch {
            when (val result = repository.setPlayed(current, !played)) {
                is ApiResult.Failure -> _state.update {
                    it.copy(error = result.error.displayMessage)
                }

                is ApiResult.Success -> {
                    val updated = result.data
                    _state.update {
                        it.copy(
                            item = updated,
                            hero = updated.media,
                            playbackItem = if (it.playbackItem?.id == updated.id) updated else it.playbackItem,
                            error = null,
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadDetail(item: DetailItem) = coroutineScope {
        val kind = kindFor(item.type)
        val seriesPlayable = if (kind == DetailKind.Series) {
            async {
                (repository.resolvePlayableTarget(item) as? ApiResult.Success)?.data
            }
        } else {
            null
        }
        val related = if (kind in setOf(DetailKind.Person, DetailKind.Artist, DetailKind.Live)) {
            null
        } else {
            async { repository.similar(item.id, 18).successOrEmpty() }
        }
        val seriesId = when (kind) {
            DetailKind.Series -> item.id
            DetailKind.Episode -> item.seriesId
            else -> null
        }
        val seasons = if (seriesId != null) {
            async { repository.seasons(seriesId).successOrEmpty() }
        } else {
            null
        }
        val children = when (kind) {
            DetailKind.Album -> async { repository.albumTracks(item.id).successOrEmpty() }
            DetailKind.Playlist -> async { repository.playlistItems(item.id).successOrEmpty() }
            DetailKind.AudioBook -> async { repository.audioBookItems(item.id).successOrEmpty() }
            DetailKind.Collection -> async { repository.collectionItems(item.id).successOrEmpty() }
            else -> null
        }
        val artistReleases = if (kind == DetailKind.Artist) {
            async { repository.artistAlbums(item.id).successOrEmpty() }
        } else {
            null
        }
        val artistTracks = if (kind == DetailKind.Artist) {
            async { repository.artistSongs(item.id).successOrEmpty() }
        } else {
            null
        }
        val personCredits = if (kind == DetailKind.Person) {
            async {
                repository.personCredits(item.id).successOrEmpty()
            }
        } else {
            null
        }

        val seasonItems = seasons?.await().orEmpty()
        val childItems = children?.await().orEmpty()
        val releaseItems = artistReleases?.await().orEmpty()
        val artistTrackItems = artistTracks?.await().orEmpty()
        val playable = when (kind) {
            DetailKind.Series -> seriesPlayable?.await()
            DetailKind.Artist ->
                choosePlayable(artistTrackItems)
                    ?.toPlayableTarget()
            DetailKind.Collection, DetailKind.Person -> null
            DetailKind.Album, DetailKind.Playlist, DetailKind.AudioBook ->
                choosePlayable(childItems)
                    ?.toPlayableTarget()

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
        val seriesNextUp = if (kind == DetailKind.Series) {
            playable?.itemId?.let { playableId ->
                (repository.item(playableId) as? ApiResult.Success)?.data
            }
        } else {
            null
        }
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
        val episodeItems = if (seriesId != null && selectedSeason != null) {
            repository.episodes(seriesId, selectedSeason).successOrEmpty()
        } else {
            emptyList()
        }

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
            episodes = episodeItems,
            tracks = childItems + artistTrackItems,
            releases = releaseItems,
            related = related?.await().orEmpty(),
            people = item.people.map(::personUi),
            credits = personCredits?.await().orEmpty(),
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

    private suspend fun ApiResult<List<MediaItemUi>>.successOrEmpty(): List<MediaItemUi> =
        (this as? ApiResult.Success)?.data.orEmpty()
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

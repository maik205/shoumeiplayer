package com.maik205.shoumeiplayer.ui.television.screens.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.domain.model.DetailMediaStream
import com.maik205.shoumeiplayer.ui.television.components.TelevisionBackground
import com.maik205.shoumeiplayer.ui.television.components.TelevisionEmptyState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionErrorState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingShape
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions

@Composable
fun TelevisionDetailScreen(
    state: TelevisionDetailState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onPlay: (
        itemId: String,
        startPositionTicks: Long,
        audioOnly: Boolean,
        audioStreamIndex: Int?,
        subtitleStreamIndex: Int?,
        qualityLabel: String?,
    ) -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onSelectSeason: (String) -> Unit,
    onOpenItem: (MediaItemUi) -> Unit,
    onOpenPerson: (PersonUi) -> Unit,
) {
    val item = state.item
    val hero = state.hero
    val playbackItem = state.playbackItem ?: item
    val playFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    var heroFocusTick by remember { mutableIntStateOf(0) }
    var initialFocusAssigned by rememberSaveable(item?.id) { mutableStateOf(false) }
    val audioStreams = remember(playbackItem?.id, playbackItem?.mediaStreams) {
        playbackItem?.mediaStreams.orEmpty().filter { it.type.equals("Audio", ignoreCase = true) }
    }
    val subtitleStreams = remember(playbackItem?.id, playbackItem?.mediaStreams) {
        playbackItem?.mediaStreams.orEmpty().filter { it.type.equals("Subtitle", ignoreCase = true) }
    }
    val qualityOptions = remember(playbackItem?.id, playbackItem?.mediaStreams) {
        detailQualityOptions(playbackItem?.mediaStreams.orEmpty())
    }
    var audioChoice by rememberSaveable(item?.id) {
        mutableIntStateOf(audioStreams.indexOfFirst(DetailMediaStream::isDefault).coerceAtLeast(0))
    }
    var subtitleChoice by rememberSaveable(item?.id) {
        mutableIntStateOf(subtitleStreams.indexOfFirst(DetailMediaStream::isDefault) + 1)
    }
    var qualityChoice by rememberSaveable(item?.id) { mutableIntStateOf(0) }

    LaunchedEffect(state.loading, state.playableItemId, item?.id, hero?.id) {
        if (!state.loading && item != null && hero != null && !initialFocusAssigned) {
            if (state.playableItemId != null) {
                playFocus.requestFocus()
            } else {
                backFocus.requestFocus()
            }
            initialFocusAssigned = true
        }
    }
    LaunchedEffect(heroFocusTick) {
        if (heroFocusTick > 0) listState.animateScrollToItem(0)
    }

    TelevisionBackground(imageUrl = hero?.backdropUrl) {
        when {
            state.loading -> TelevisionLoadingState(
                shape = TelevisionLoadingShape.Detail,
                modifier = Modifier.padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    top = 210.dp,
                ),
            )

            state.error != null && item == null -> TelevisionErrorState(
                title = "This title is out of reach",
                message = state.error,
                onRetry = onRetry,
                modifier = Modifier.padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    top = 210.dp,
                ),
            )

            item == null || hero == null -> TelevisionEmptyState(
                title = "Nothing to show",
                modifier = Modifier.padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    top = 210.dp,
                ),
            )

            else -> {
                val targetMetadata = state.nextUp ?: state.playableItemId?.let { id ->
                    (state.episodes + state.tracks).firstOrNull { it.id == id }
                } ?: hero
                val isAudio = state.kind in setOf(
                    DetailKind.Album,
                    DetailKind.Artist,
                    DetailKind.Playlist,
                    DetailKind.AudioBook,
                ) || targetMetadata.type == "Audio"
                val relatedItems = if (state.kind == DetailKind.Series) {
                    state.related.filter { it.type == "Series" }
                } else {
                    state.related
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer(),
                    contentPadding = PaddingValues(bottom = 72.dp),
                ) {
                    item(key = "hero") {
                        DetailHero(
                            modifier = Modifier.fillParentMaxHeight(0.76f),
                            state = state,
                            audioStreams = audioStreams,
                            subtitleStreams = subtitleStreams,
                            qualityOptions = qualityOptions,
                            audioChoice = audioChoice,
                            subtitleChoice = subtitleChoice,
                            qualityChoice = qualityChoice,
                            onSelectAudio = { audioChoice = it },
                            onSelectSubtitle = { subtitleChoice = it },
                            onSelectQuality = { qualityChoice = it },
                            onBack = onBack,
                            backFocus = backFocus,
                            onToggleFavorite = onToggleFavorite,
                            onTogglePlayed = onTogglePlayed,
                            onHeroControlFocused = { heroFocusTick += 1 },
                            onPlay = {
                                onPlay(
                                    state.playableItemId ?: hero.id,
                                    state.playableResumeTicks,
                                    isAudio,
                                    audioStreams.getOrNull(audioChoice)?.index,
                                    subtitleStreams.getOrNull(subtitleChoice - 1)?.index,
                                    qualityOptions.getOrNull(qualityChoice),
                                )
                            },
                            playFocus = playFocus,
                        )
                    }

                    if (
                        state.kind == DetailKind.Series &&
                        (state.nextUp != null || state.seasons.isNotEmpty() || state.episodes.isNotEmpty())
                    ) {
                        item(key = "series") {
                            SeriesDetailSection(
                                nextUp = state.nextUp,
                                nextUpItem = state.playbackItem,
                                seasons = state.seasons,
                                selectedSeasonId = state.selectedSeasonId,
                                onSelect = onSelectSeason,
                                episodes = state.episodes,
                                onPlay = { episode ->
                                    onPlay(
                                        episode.id,
                                        episode.resumeTicks,
                                        false,
                                        audioStreams.getOrNull(audioChoice)?.index,
                                        subtitleStreams.getOrNull(subtitleChoice - 1)?.index,
                                        qualityOptions.getOrNull(qualityChoice),
                                    )
                                },
                            )
                        }
                    } else if (state.kind == DetailKind.Episode && state.episodes.isNotEmpty()) {
                        item(key = "episode-context") {
                            SeriesDetailSection(
                                nextUp = null,
                                nextUpItem = null,
                                seasons = state.seasons,
                                selectedSeasonId = state.selectedSeasonId,
                                onSelect = onSelectSeason,
                                episodes = state.episodes,
                                episodeTitle = "More episodes",
                                currentEpisodeId = item.id,
                                onPlay = { episode ->
                                    onPlay(
                                        episode.id,
                                        episode.resumeTicks,
                                        false,
                                        audioStreams.getOrNull(audioChoice)?.index,
                                        subtitleStreams.getOrNull(subtitleChoice - 1)?.index,
                                        qualityOptions.getOrNull(qualityChoice),
                                    )
                                },
                            )
                        }
                    }

                    if (state.tracks.isNotEmpty()) {
                        item(key = "tracks") {
                            TrackRail(
                                title = when (state.kind) {
                                    DetailKind.AudioBook -> "Chapters"
                                    DetailKind.Artist -> "Songs"
                                    else -> "Tracks"
                                },
                                tracks = state.tracks,
                                onPlay = { track ->
                                    onPlay(track.id, track.resumeTicks, true, null, null, null)
                                },
                            )
                        }
                    }

                    if (state.releases.isNotEmpty()) {
                        item(key = "releases") {
                            DetailMediaRail(
                                title = "Albums",
                                items = state.releases,
                                onOpen = onOpenItem,
                            )
                        }
                    }

                    if (relatedItems.isNotEmpty()) {
                        item(key = "related") {
                            DetailMediaRail(
                                title = "More like this",
                                items = relatedItems,
                                onOpen = onOpenItem,
                            )
                        }
                    }

                    if (state.people.isNotEmpty()) {
                        item(key = "people") {
                            PeopleRail(
                                people = state.people,
                                onOpen = onOpenPerson,
                            )
                        }
                    }

                    if (state.credits.isNotEmpty()) {
                        item(key = "credits") {
                            DetailMediaRail(
                                title = "Known for",
                                items = state.credits,
                                onOpen = onOpenItem,
                            )
                        }
                    }

                    item(key = "about") {
                        DetailAbout(state)
                    }
                }
            }
        }
    }
}

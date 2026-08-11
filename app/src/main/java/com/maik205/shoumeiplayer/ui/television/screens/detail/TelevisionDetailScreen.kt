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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.model.DetailMediaStream
import com.maik205.shoumeiplayer.ui.television.components.TelevisionBackground
import com.maik205.shoumeiplayer.ui.television.components.TelevisionEmptyState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionErrorState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingShape
import com.maik205.shoumeiplayer.ui.television.components.rememberPlaybackLaunchState
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.ui.i18n.UiText
import com.maik205.shoumeiplayer.ui.i18n.resolve
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions

@Composable
fun TelevisionDetailScreen(
    state: TelevisionDetailState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onRetryAction: () -> Unit,
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
    val loadError = state.error
    val actionError = state.actionError
    val playableSectionError = state.sectionErrors[DetailSection.Playable]
    val seasonsSectionError = state.sectionErrors[DetailSection.Seasons]
    val episodesSectionError = state.sectionErrors[DetailSection.Episodes]
    val playbackItem = state.playbackItem ?: item
    val playFocus = remember { FocusRequester() }
    val backFocus = remember { FocusRequester() }
    val firstSectionFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val playbackLaunch = rememberPlaybackLaunchState(item?.id)
    val heroListIndex = (if (loadError != null) 1 else 0) + (if (actionError != null) 1 else 0)
    var initialFocusAssigned by rememberSaveable(item?.id) { mutableStateOf(false) }
    var retryHadFocus by remember(item?.id) { mutableStateOf(false) }
    var previousErrorKeys by remember(item?.id) { mutableStateOf<Set<String>?>(null) }
    val errorKeys = buildSet {
        if (loadError != null) add("load")
        if (actionError != null) add("action")
        if (state.seasonError != null) add("season")
        state.sectionErrors.keys.forEach { add("section:${it.name}") }
    }
    val audioStreams = remember(playbackItem?.id, playbackItem?.mediaStreams) {
        playbackItem?.mediaStreams.orEmpty().filter { it.type.equals("Audio", ignoreCase = true) }
    }
    val subtitleStreams = remember(playbackItem?.id, playbackItem?.mediaStreams) {
        playbackItem?.mediaStreams.orEmpty().filter { it.type.equals("Subtitle", ignoreCase = true) }
    }
    val qualityOptions = detailQualityOptions(playbackItem?.mediaStreams.orEmpty())
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
    LaunchedEffect(errorKeys) {
        val removedErrors = previousErrorKeys.orEmpty() - errorKeys
        if (shouldRestoreDetailRetryFocus(removedErrors, retryHadFocus) && item != null && hero != null) {
            withFrameNanos { }
            if (state.playableItemId != null) playFocus.requestFocus() else backFocus.requestFocus()
            retryHadFocus = false
        }
        previousErrorKeys = errorKeys
    }
    TelevisionBackground(imageUrl = hero?.backdropUrl) {
        when {
            state.loading -> TelevisionLoadingState(
                label = stringResource(R.string.tv_loading_detail),
                shape = TelevisionLoadingShape.Detail,
                modifier = Modifier.padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    top = 210.dp,
                ),
            )

            loadError != null && item == null -> Column(
                modifier = Modifier.padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    top = 210.dp,
                ),
            ) {
                TelevisionFocusRevealButton(
                    label = stringResource(R.string.tv_back),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    expandedWidth = 92.dp,
                )
                Spacer(Modifier.height(28.dp))
                TelevisionErrorState(
                    title = stringResource(R.string.tv_detail_error_title),
                    message = loadError.resolve(),
                    onRetry = onRetry,
                    retryLabel = stringResource(R.string.retry),
                    requestInitialFocus = true,
                )
            }

            item == null || hero == null -> Column(
                modifier = Modifier.padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    top = 210.dp,
                ),
            ) {
                TelevisionFocusRevealButton(
                    label = stringResource(R.string.tv_back),
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onBack,
                    expandedWidth = 92.dp,
                )
                Spacer(Modifier.height(28.dp))
                TelevisionEmptyState(
                    title = stringResource(R.string.tv_detail_empty_title),
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    requestInitialFocus = true,
                )
            }

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
                val hasSeriesContent = when (state.kind) {
                    DetailKind.Series -> state.nextUp != null ||
                        state.seasons.isNotEmpty() || state.episodes.isNotEmpty()
                    DetailKind.Episode -> state.episodes.isNotEmpty()
                    else -> false
                }
                val firstContentSection = firstDetailContentFocusTarget(
                    hasSeriesContent = hasSeriesContent,
                    hasTracks = state.tracks.isNotEmpty(),
                    hasReleases = state.releases.isNotEmpty(),
                    hasRelated = relatedItems.isNotEmpty(),
                    hasPeople = state.people.isNotEmpty(),
                    hasCredits = state.credits.isNotEmpty(),
                )
                val heroReturnFocus = if (state.playableItemId != null) playFocus else backFocus

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer(),
                    contentPadding = PaddingValues(bottom = 72.dp),
                ) {
                    if (loadError != null) {
                        item(key = "detail-load-error") {
                            TelevisionErrorState(
                                title = stringResource(R.string.tv_detail_error_title),
                                message = loadError.resolve(),
                                onRetry = onRetry,
                                retryLabel = stringResource(R.string.retry),
                                requestInitialFocus = false,
                                modifier = Modifier.padding(
                                    start = TelevisionDimensions.SafeHorizontal,
                                    end = TelevisionDimensions.SafeHorizontal,
                                    top = 24.dp,
                                ).onFocusChanged { if (it.hasFocus) retryHadFocus = true },
                            )
                        }
                    }

                    if (actionError != null) {
                        item(key = "detail-action-error") {
                            TelevisionErrorState(
                                title = stringResource(R.string.tv_detail_action_error_title),
                                message = actionError.resolve(),
                                onRetry = onRetryAction,
                                retryLabel = stringResource(R.string.retry),
                                requestInitialFocus = false,
                                modifier = Modifier.padding(
                                    start = TelevisionDimensions.SafeHorizontal,
                                    end = TelevisionDimensions.SafeHorizontal,
                                    top = 24.dp,
                                ).onFocusChanged { if (it.hasFocus) retryHadFocus = true },
                            )
                        }
                    }

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
                            onHeroControlFocused = {
                                listState.requestScrollToItem(heroListIndex)
                            },
                            onPlay = {
                                playbackLaunch.launch {
                                    onPlay(
                                        state.playableItemId ?: hero.id,
                                        state.playableResumeTicks,
                                        isAudio,
                                        audioStreams.getOrNull(audioChoice)?.index,
                                        subtitleStreams.getOrNull(subtitleChoice - 1)?.index,
                                        qualityOptions.getOrNull(qualityChoice),
                                    )
                                }
                            },
                            playLoading = playbackLaunch.loading,
                            playFocus = playFocus,
                            firstSectionFocus = firstSectionFocus.takeIf { firstContentSection != null },
                        )
                    }

                    if (playableSectionError != null) {
                        item(key = "playable-error") {
                            DetailSectionError(
                                title = stringResource(R.string.tv_detail_playable_error_title),
                                error = playableSectionError,
                                onRetry = onRetry,
                                onRetryFocused = { retryHadFocus = true },
                            )
                        }
                    }
                    if (seasonsSectionError != null) {
                        item(key = "seasons-error") {
                            DetailSectionError(
                                title = stringResource(R.string.tv_detail_seasons_error_title),
                                error = seasonsSectionError,
                                onRetry = onRetry,
                                onRetryFocused = { retryHadFocus = true },
                            )
                        }
                    }
                    if (episodesSectionError != null) {
                        item(key = "episodes-load-error") {
                            DetailSectionError(
                                title = stringResource(R.string.tv_detail_episodes_error_title),
                                error = episodesSectionError,
                                onRetry = onRetry,
                                onRetryFocused = { retryHadFocus = true },
                            )
                        }
                    }

                    if (
                        state.kind == DetailKind.Series &&
                        (
                            state.nextUp != null ||
                                state.seasons.isNotEmpty() ||
                                state.episodes.isNotEmpty() ||
                                state.sectionErrors.keys.any {
                                    it == DetailSection.Playable ||
                                        it == DetailSection.Seasons ||
                                        it == DetailSection.Episodes
                                }
                            )
                    ) {
                        if (state.seasonLoading) {
                            item(key = "season-loading") {
                                TelevisionLoadingState(
                                    label = stringResource(R.string.tv_loading_episodes),
                                    shape = TelevisionLoadingShape.Rail,
                                    modifier = Modifier.padding(
                                        start = TelevisionDimensions.SafeHorizontal,
                                        end = TelevisionDimensions.SafeHorizontal,
                                        top = 30.dp,
                                    ),
                                )
                            }
                        }
                        state.seasonError?.let { error ->
                            item(key = "season-error") {
                                TelevisionErrorState(
                                    title = stringResource(R.string.tv_detail_episodes_error_title),
                                    message = error.resolve(),
                                    onRetry = {
                                        state.seasonErrorId?.let(onSelectSeason)
                                    },
                                    retryLabel = stringResource(R.string.retry),
                                    requestInitialFocus = false,
                                    modifier = Modifier.padding(
                                        start = TelevisionDimensions.SafeHorizontal,
                                        end = TelevisionDimensions.SafeHorizontal,
                                        top = 22.dp,
                                    ).onFocusChanged { if (it.hasFocus) retryHadFocus = true },
                                )
                            }
                        }
                        item(key = "series") {
                            SeriesDetailSection(
                                nextUp = state.nextUp,
                                nextUpItem = state.playbackItem,
                                seasons = state.seasons,
                                selectedSeasonId = state.selectedSeasonId,
                                onSelect = onSelectSeason,
                                episodes = state.episodes,
                                episodeTitle = stringResource(R.string.tv_episodes),
                                onPlay = { episode ->
                                    playbackLaunch.launch {
                                        onPlay(
                                            episode.id,
                                            episode.resumeTicks,
                                            false,
                                            audioStreams.getOrNull(audioChoice)?.index,
                                            subtitleStreams.getOrNull(subtitleChoice - 1)?.index,
                                            qualityOptions.getOrNull(qualityChoice),
                                        )
                                    }
                                },
                                entryFocusRequester = firstSectionFocus.takeIf { firstContentSection == DetailContentFocusTarget.Series },
                                heroFocusRequester = heroReturnFocus,
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
                                episodeTitle = stringResource(R.string.tv_detail_more_episodes),
                                currentEpisodeId = item.id,
                                onPlay = { episode ->
                                    playbackLaunch.launch {
                                        onPlay(
                                            episode.id,
                                            episode.resumeTicks,
                                            false,
                                            audioStreams.getOrNull(audioChoice)?.index,
                                            subtitleStreams.getOrNull(subtitleChoice - 1)?.index,
                                            qualityOptions.getOrNull(qualityChoice),
                                        )
                                    }
                                },
                                entryFocusRequester = firstSectionFocus.takeIf { firstContentSection == DetailContentFocusTarget.Series },
                                heroFocusRequester = heroReturnFocus,
                            )
                        }
                    }

                    state.sectionErrors[DetailSection.Tracks]?.let { error ->
                        item(key = "tracks-error") {
                            TelevisionErrorState(
                                title = stringResource(R.string.tv_detail_tracks_error_title),
                                message = error.resolve(),
                                onRetry = onRetry,
                                retryLabel = stringResource(R.string.retry),
                                requestInitialFocus = false,
                                modifier = Modifier
                                    .padding(horizontal = TelevisionDimensions.SafeHorizontal)
                                    .onFocusChanged { if (it.hasFocus) retryHadFocus = true },
                            )
                        }
                    }

                    if (state.tracks.isNotEmpty()) {
                        item(key = "tracks") {
                            TrackRail(
                                title = when (state.kind) {
                                    DetailKind.AudioBook -> stringResource(R.string.tv_detail_chapters)
                                    DetailKind.Artist -> stringResource(R.string.tv_detail_songs)
                                    else -> stringResource(R.string.tv_detail_tracks)
                                },
                                tracks = state.tracks,
                                onPlay = { track ->
                                    playbackLaunch.launch {
                                        onPlay(track.id, track.resumeTicks, true, null, null, null)
                                    }
                                },
                                entryFocusRequester = firstSectionFocus.takeIf { firstContentSection == DetailContentFocusTarget.Tracks },
                                heroFocusRequester = heroReturnFocus,
                            )
                        }
                    }

                    if (state.releases.isNotEmpty()) {
                        item(key = "releases") {
                            DetailMediaRail(
                                title = stringResource(R.string.tv_detail_albums),
                                items = state.releases,
                                onOpen = onOpenItem,
                                entryFocusRequester = firstSectionFocus.takeIf { firstContentSection == DetailContentFocusTarget.Releases },
                                heroFocusRequester = heroReturnFocus,
                            )
                        }
                    }

                    state.sectionErrors[DetailSection.Releases]?.let { error ->
                        item(key = "releases-error") {
                            TelevisionErrorState(
                                title = stringResource(R.string.tv_detail_releases_error_title),
                                message = error.resolve(),
                                onRetry = onRetry,
                                retryLabel = stringResource(R.string.retry),
                                requestInitialFocus = false,
                                modifier = Modifier
                                    .padding(horizontal = TelevisionDimensions.SafeHorizontal)
                                    .onFocusChanged { if (it.hasFocus) retryHadFocus = true },
                            )
                        }
                    }

                    if (relatedItems.isNotEmpty()) {
                        item(key = "related") {
                            DetailMediaRail(
                                title = stringResource(R.string.tv_detail_more_like),
                                items = relatedItems,
                                onOpen = onOpenItem,
                                entryFocusRequester = firstSectionFocus.takeIf { firstContentSection == DetailContentFocusTarget.Related },
                                heroFocusRequester = heroReturnFocus,
                            )
                        }
                    }

                    state.sectionErrors[DetailSection.Related]?.let { error ->
                        item(key = "related-error") {
                            TelevisionErrorState(
                                title = stringResource(R.string.tv_detail_related_error_title),
                                message = error.resolve(),
                                onRetry = onRetry,
                                retryLabel = stringResource(R.string.retry),
                                requestInitialFocus = false,
                                modifier = Modifier
                                    .padding(horizontal = TelevisionDimensions.SafeHorizontal)
                                    .onFocusChanged { if (it.hasFocus) retryHadFocus = true },
                            )
                        }
                    }

                    if (state.people.isNotEmpty()) {
                        item(key = "people") {
                            PeopleRail(
                                people = state.people,
                                onOpen = onOpenPerson,
                                entryFocusRequester = firstSectionFocus.takeIf { firstContentSection == DetailContentFocusTarget.People },
                                heroFocusRequester = heroReturnFocus,
                            )
                        }
                    }

                    if (state.credits.isNotEmpty()) {
                        item(key = "credits") {
                            DetailMediaRail(
                                title = stringResource(R.string.tv_detail_known_for),
                                items = state.credits,
                                onOpen = onOpenItem,
                                entryFocusRequester = firstSectionFocus.takeIf { firstContentSection == DetailContentFocusTarget.Credits },
                                heroFocusRequester = heroReturnFocus,
                            )
                        }
                    }

                    state.sectionErrors[DetailSection.Credits]?.let { error ->
                        item(key = "credits-error") {
                            TelevisionErrorState(
                                title = stringResource(R.string.tv_detail_credits_error_title),
                                message = error.resolve(),
                                onRetry = onRetry,
                                retryLabel = stringResource(R.string.retry),
                                requestInitialFocus = false,
                                modifier = Modifier.padding(horizontal = TelevisionDimensions.SafeHorizontal),
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

internal enum class DetailContentFocusTarget {
    Series,
    Tracks,
    Releases,
    Related,
    People,
    Credits,
}

internal fun firstDetailContentFocusTarget(
    hasSeriesContent: Boolean,
    hasTracks: Boolean,
    hasReleases: Boolean,
    hasRelated: Boolean,
    hasPeople: Boolean,
    hasCredits: Boolean,
): DetailContentFocusTarget? = when {
    hasSeriesContent -> DetailContentFocusTarget.Series
    hasTracks -> DetailContentFocusTarget.Tracks
    hasReleases -> DetailContentFocusTarget.Releases
    hasRelated -> DetailContentFocusTarget.Related
    hasPeople -> DetailContentFocusTarget.People
    hasCredits -> DetailContentFocusTarget.Credits
    else -> null
}

internal fun shouldRestoreDetailRetryFocus(
    removedErrors: Set<String>,
    retryHadFocus: Boolean,
): Boolean = retryHadFocus && removedErrors.isNotEmpty()

@Composable
private fun DetailSectionError(
    title: String,
    error: UiText,
    onRetry: () -> Unit,
    onRetryFocused: () -> Unit,
) {
    TelevisionErrorState(
        title = title,
        message = error.resolve(),
        onRetry = onRetry,
        retryLabel = stringResource(R.string.retry),
        requestInitialFocus = false,
        modifier = Modifier
            .padding(
                start = TelevisionDimensions.SafeHorizontal,
                end = TelevisionDimensions.SafeHorizontal,
                top = 22.dp,
            )
            .onFocusChanged { if (it.hasFocus) onRetryFocused() },
    )
}

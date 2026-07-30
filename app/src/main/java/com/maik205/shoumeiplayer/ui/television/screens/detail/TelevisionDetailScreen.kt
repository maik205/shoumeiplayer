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
import com.maik205.shoumeiplayer.data.api.dto.MediaStreamDto
import com.maik205.shoumeiplayer.ui.television.components.TelevisionBackground
import com.maik205.shoumeiplayer.ui.television.components.TelevisionEmptyState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionErrorState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.model.MediaItemUi
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
        mutableIntStateOf(audioStreams.indexOfFirst(MediaStreamDto::isDefault).coerceAtLeast(0))
    }
    var subtitleChoice by rememberSaveable(item?.id) {
        mutableIntStateOf(subtitleStreams.indexOfFirst(MediaStreamDto::isDefault) + 1)
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

@Composable
private fun DetailHero(
    modifier: Modifier = Modifier,
    state: TelevisionDetailState,
    audioStreams: List<MediaStreamDto>,
    subtitleStreams: List<MediaStreamDto>,
    qualityOptions: List<String>,
    audioChoice: Int,
    subtitleChoice: Int,
    qualityChoice: Int,
    onSelectAudio: (Int) -> Unit,
    onSelectSubtitle: (Int) -> Unit,
    onSelectQuality: (Int) -> Unit,
    onBack: () -> Unit,
    backFocus: FocusRequester,
    onToggleFavorite: () -> Unit,
    onTogglePlayed: () -> Unit,
    onHeroControlFocused: () -> Unit,
    onPlay: () -> Unit,
    playFocus: FocusRequester,
) {
    val item = requireNotNull(state.item)
    val playbackItem = state.playbackItem ?: item
    val hero = requireNotNull(state.hero)
    val hasTrackChoices = playbackItem.mediaStreams.any {
        it.type.equals("Video", true) ||
            it.type.equals("Audio", true) ||
            it.type.equals("Subtitle", true)
    }

    Box(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .width(690.dp)
                .padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    top = TelevisionDimensions.SafeTop,
                ),
        ) {
            TelevisionFocusRevealButton(
                label = "Back",
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                focusRequester = backFocus,
                expandedWidth = 92.dp,
            )
            Spacer(Modifier.height(44.dp))
            Text(
                text = detailEyebrow(state),
                style = MaterialTheme.typography.labelLarge,
                color = TelevisionColors.PaperMuted,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = hero.title,
                style = MaterialTheme.typography.displayLarge.copy(fontSize = 54.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val metadata = detailMetadata(state)
            if (metadata.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = metadata.take(5).joinToString("   "),
                    style = MaterialTheme.typography.bodySmall,
                    color = TelevisionColors.PaperMuted,
                    maxLines = 1,
                )
            }
            hero.overview?.let { overview ->
                Spacer(Modifier.height(18.dp))
                Text(
                    text = overview,
                    modifier = Modifier.width(540.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TelevisionColors.PaperSoft,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isPlayable) {
                    TelevisionFocusRevealButton(
                        label = detailPlayLabel(state),
                        icon = Icons.Default.PlayArrow,
                        onClick = onPlay,
                        selected = true,
                        focusRequester = playFocus,
                        expandedWidth = if (state.kind == DetailKind.Series) 148.dp else 96.dp,
                        onFocusChanged = { if (it) onHeroControlFocused() },
                    )
                }
                TelevisionFocusRevealButton(
                    label = if (hero.favorite) "In my list" else "My list",
                    icon = if (hero.favorite) Icons.Default.Check else Icons.Default.Add,
                    onClick = onToggleFavorite,
                    expandedWidth = 118.dp,
                    onFocusChanged = { if (it) onHeroControlFocused() },
                )
                if (
                    state.kind !in setOf(
                        DetailKind.Person,
                        DetailKind.Artist,
                        DetailKind.Collection,
                        DetailKind.Series,
                        DetailKind.Episode,
                    )
                ) {
                    TelevisionFocusRevealButton(
                        label = if (hero.watched) "Mark unwatched" else "Mark watched",
                        icon = if (hero.watched) Icons.Default.Replay else Icons.Default.DoneAll,
                        onClick = onTogglePlayed,
                        expandedWidth = 156.dp,
                        onFocusChanged = { if (it) onHeroControlFocused() },
                    )
                }
            }

            if (hasTrackChoices && state.kind !in setOf(DetailKind.Artist, DetailKind.Collection)) {
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    if (audioStreams.isNotEmpty()) {
                        DetailChoiceField(
                            label = "Audio",
                            options = audioStreams.map { it.streamLabel("Default") },
                            selectedIndex = audioChoice,
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            onSelect = onSelectAudio,
                            onFocused = onHeroControlFocused,
                        )
                    }
                    DetailChoiceField(
                        label = "Subtitles",
                        options = listOf("Off") + subtitleStreams.map { it.streamLabel("On") },
                        selectedIndex = subtitleChoice,
                        icon = Icons.Default.ClosedCaption,
                        onSelect = onSelectSubtitle,
                        onFocused = onHeroControlFocused,
                    )
                    DetailChoiceField(
                        label = "Quality",
                        options = qualityOptions,
                        selectedIndex = qualityChoice,
                        icon = Icons.Default.HighQuality,
                        onSelect = onSelectQuality,
                        onFocused = onHeroControlFocused,
                    )
                }
            }
        }

        val heroArt = hero.logoUrl ?: if (
            state.kind in setOf(
                DetailKind.Album,
                DetailKind.Artist,
                DetailKind.Playlist,
                DetailKind.AudioBook,
            )
        ) {
            hero.imageUrl
        } else {
            null
        }
        heroArt?.let {
            AsyncImage(
                model = it,
                contentDescription = hero.title,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = TelevisionDimensions.SafeHorizontal + 28.dp)
                    .size(
                        width = if (hero.logoUrl != null) 350.dp else 260.dp,
                        height = if (hero.logoUrl != null) 150.dp else 260.dp,
                    ),
                alignment = Alignment.Center,
            )
        }
    }
}

private fun MediaStreamDto?.streamLabel(fallback: String): String {
    if (this == null) return fallback
    return displayTitle
        ?.takeIf(String::isNotBlank)
        ?: listOfNotNull(
            language?.uppercase()?.takeIf(String::isNotBlank),
            codec?.uppercase()?.takeIf(String::isNotBlank),
            channels?.let { "$it ch" },
        ).joinToString(" ").ifBlank { fallback }
}

private fun detailQualityOptions(streams: List<MediaStreamDto>): List<String> {
    val height = streams.firstOrNull { it.type.equals("Video", true) }?.height
    return buildList {
        add("Auto")
        if (height == null || height >= 2160) add("4K")
        if (height == null || height >= 1080) add("1080p")
        if (height == null || height >= 720) add("720p")
        add("480p")
    }
}

private fun detailEyebrow(state: TelevisionDetailState): String {
    val item = state.item ?: return detailKindLabel(state.kind)
    if (state.kind == DetailKind.Episode) {
        return listOfNotNull(
            item.seriesName?.takeIf(String::isNotBlank),
            episodeLabel(item.parentIndexNumber, item.indexNumber),
        ).joinToString("  ·  ").ifBlank { "Episode" }
    }
    return detailKindLabel(state.kind)
}

private fun detailMetadata(state: TelevisionDetailState): List<String> {
    val item = state.item ?: return emptyList()
    return when (state.kind) {
        DetailKind.Series -> buildList {
            item.productionYear?.let { add(it.toString()) }
            item.episodeCount?.takeIf { it > 0 }?.let { add("$it episodes") }
            playbackQualityLabel(state.playbackItem?.mediaStreams.orEmpty())?.let(::add)
            item.genres.firstOrNull()?.takeIf(String::isNotBlank)?.let(::add)
            item.officialRating?.takeIf(String::isNotBlank)?.let(::add)
        }

        DetailKind.Episode -> buildList {
            item.runTimeTicks?.takeIf { it > 0 }?.let {
                state.hero?.runtimeLabel?.let(::add)
            }
            detailDateLabel(item.premiereDate)?.let(::add)
            item.officialRating?.takeIf(String::isNotBlank)?.let(::add)
            playbackQualityLabel(state.playbackItem?.mediaStreams.orEmpty())?.let(::add)
        }

        else -> state.hero?.metadata.orEmpty()
    }
}

private fun detailPlayLabel(state: TelevisionDetailState): String {
    val action = if (state.playableResumeTicks > 0) "Resume" else "Play"
    if (state.kind != DetailKind.Series) return action
    val nextUp = state.nextUp ?: return action
    return listOfNotNull(
        action,
        episodeLabel(nextUp.seasonNumber, nextUp.episodeNumber),
    ).joinToString(" ")
}

private fun episodeLabel(seasonNumber: Int?, episodeNumber: Int?): String? =
    if (seasonNumber != null || episodeNumber != null) {
        "S${seasonNumber ?: 0} E${episodeNumber ?: 0}"
    } else {
        null
    }

private fun playbackQualityLabel(streams: List<MediaStreamDto>): String? {
    val height = streams.firstOrNull { it.type.equals("Video", true) }?.height ?: return null
    return when {
        height >= 2160 -> "4K"
        height >= 1080 -> "1080p"
        height >= 720 -> "720p"
        else -> "${height}p"
    }
}

private fun detailKindLabel(kind: DetailKind): String = when (kind) {
    DetailKind.Film -> "Movie"
    DetailKind.Series -> "Series"
    DetailKind.Episode -> "Episode"
    DetailKind.Album -> "Album"
    DetailKind.Artist -> "Artist"
    DetailKind.Playlist -> "Playlist"
    DetailKind.AudioBook -> "Audiobook"
    DetailKind.Collection -> "Collection"
    DetailKind.Person -> "Person"
    DetailKind.Live -> "Live"
    DetailKind.Generic -> "Media"
}

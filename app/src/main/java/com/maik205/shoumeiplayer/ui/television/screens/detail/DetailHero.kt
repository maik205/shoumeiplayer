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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.di.LocalAppContainer
import com.maik205.shoumeiplayer.domain.model.DetailMediaStream
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.ui.television.components.TelevisionBackground
import com.maik205.shoumeiplayer.ui.television.components.TelevisionEmptyState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionErrorState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingShape
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import com.maik205.shoumeiplayer.ui.television.theme.televisionTypography

@Composable
internal fun DetailHero(
    modifier: Modifier = Modifier,
    state: TelevisionDetailState,
    audioStreams: List<DetailMediaStream>,
    subtitleStreams: List<DetailMediaStream>,
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
    playLoading: Boolean,
    playFocus: FocusRequester,
    /** The first section below the hero, so Down out of the hero row is defined (DETAIL-005). */
    downFocusRequester: FocusRequester? = null,
) {
    val item = requireNotNull(state.item)
    val playbackItem = state.playbackItem ?: item
    val hero = requireNotNull(state.hero)
    val hasTrackChoices = playbackItem.mediaStreams.any {
        it.type.equals("Video", true) ||
            it.type.equals("Audio", true) ||
            it.type.equals("Subtitle", true)
    }
    val defaultStreamLabel = stringResource(R.string.tv_default)
    val subtitleFallbackLabel = stringResource(R.string.tv_on)
    val audioOptions = mutableListOf<String>()
    for (stream in audioStreams) {
        audioOptions += stream.streamLabel(defaultStreamLabel)
    }
    val subtitleOptions = mutableListOf(stringResource(R.string.off))
    for (stream in subtitleStreams) {
        subtitleOptions += stream.streamLabel(subtitleFallbackLabel)
    }

    // Same seam as TelevisionHomeScreen/TelevisionLibraryScreen: read the store directly so
    // "Interface scale" (#89) reaches the detail hero's text without threading a ClientSettings
    // field through TelevisionDetailState.
    val settingsStore = LocalAppContainer.current.settingsStore
    val settingsDefaults = remember { ClientSettings() }
    val settings by settingsStore.settings.collectAsState(initial = settingsDefaults)

    MaterialTheme(typography = televisionTypography(settings.interfaceScale)) {
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
                label = stringResource(R.string.tv_back),
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                onClick = onBack,
                focusRequester = backFocus,
                expandedWidth = 92.dp,
            )
            Spacer(Modifier.height(44.dp))
            Text(
                text = detailEyebrow(state),
                style = MaterialTheme.typography.labelLarge,
                color = TelevisionTheme.colors.PaperMuted,
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
                    color = TelevisionTheme.colors.PaperMuted,
                    maxLines = 1,
                )
            }
            hero.overview?.let { overview ->
                Spacer(Modifier.height(18.dp))
                Text(
                    text = overview,
                    modifier = Modifier.width(540.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    color = TelevisionTheme.colors.PaperSoft,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(22.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.isPlayable) {
                    TelevisionFocusRevealButton(
                        label = if (playLoading) {
                            stringResource(R.string.tv_player_loading)
                        } else {
                            detailPlayLabel(state)
                        },
                        icon = Icons.Default.PlayArrow,
                        onClick = onPlay,
                        loading = playLoading,
                        selected = true,
                        focusRequester = playFocus,
                        expandedWidth = if (state.kind == DetailKind.Series) 148.dp else 96.dp,
                        onFocusChanged = { if (it) onHeroControlFocused() },
                        modifier = Modifier.focusProperties {
                            downFocusRequester?.let { down = it }
                        },
                    )
                }
                TelevisionFocusRevealButton(
                    label = if (hero.favorite) {
                        stringResource(R.string.tv_in_my_list)
                    } else {
                        stringResource(R.string.tv_my_list)
                    },
                    icon = if (hero.favorite) Icons.Default.Check else Icons.Default.Add,
                    onClick = onToggleFavorite,
                    expandedWidth = 118.dp,
                    onFocusChanged = { if (it) onHeroControlFocused() },
                    modifier = Modifier.focusProperties {
                        downFocusRequester?.let { down = it }
                    },
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
                        label = if (hero.watched) {
                            stringResource(R.string.tv_mark_unwatched)
                        } else {
                            stringResource(R.string.tv_mark_watched)
                        },
                        icon = if (hero.watched) Icons.Default.Replay else Icons.Default.DoneAll,
                        onClick = onTogglePlayed,
                        expandedWidth = 156.dp,
                        onFocusChanged = { if (it) onHeroControlFocused() },
                        modifier = Modifier.focusProperties {
                            downFocusRequester?.let { down = it }
                        },
                    )
                }
            }

            if (hasTrackChoices && state.kind !in setOf(DetailKind.Artist, DetailKind.Collection)) {
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                    if (audioStreams.isNotEmpty()) {
                        DetailChoiceField(
                            label = stringResource(R.string.tv_audio),
                            options = audioOptions,
                            selectedIndex = audioChoice,
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            onSelect = onSelectAudio,
                            onFocused = onHeroControlFocused,
                        )
                    }
                    DetailChoiceField(
                        label = stringResource(R.string.tv_subtitles),
                        options = subtitleOptions,
                        selectedIndex = subtitleChoice,
                        icon = Icons.Default.ClosedCaption,
                        onSelect = onSelectSubtitle,
                        onFocused = onHeroControlFocused,
                    )
                    DetailChoiceField(
                        label = stringResource(R.string.tv_quality),
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
}

@Composable
private fun DetailMediaStream?.streamLabel(fallback: String): String {
    if (this == null) return fallback
    val title = displayTitle?.takeIf(String::isNotBlank)
    if (title != null) return title
    val labels = mutableListOf<String>()
    language?.uppercase()?.takeIf(String::isNotBlank)?.let(labels::add)
    codec?.uppercase()?.takeIf(String::isNotBlank)?.let(labels::add)
    val channelCount = channels
    if (channelCount != null) labels += stringResource(R.string.tv_channels_count, channelCount)
    val label = labels.joinToString(" ")
    return if (label.isBlank()) fallback else label
}

@Composable
internal fun detailQualityOptions(streams: List<DetailMediaStream>): List<String> {
    val height = streams.firstOrNull { it.type.equals("Video", true) }?.height
    val options = mutableListOf(stringResource(R.string.tv_auto))
    if (height == null || height >= 2160) options += stringResource(R.string.tv_resolution_4k)
    if (height == null || height >= 1080) options += stringResource(R.string.tv_resolution_1080p)
    if (height == null || height >= 720) options += stringResource(R.string.tv_resolution_720p)
    options += stringResource(R.string.tv_resolution_480p)
    return options
}

@Composable
private fun detailEyebrow(state: TelevisionDetailState): String {
    val item = state.item ?: return detailKindLabel(state.kind)
    if (state.kind == DetailKind.Episode) {
        val label = listOfNotNull(
            item.seriesName?.takeIf(String::isNotBlank),
            episodeLabel(item.parentIndexNumber, item.indexNumber),
        ).joinToString("  ·  ")
        return if (label.isBlank()) stringResource(R.string.tv_episode) else label
    }
    return detailKindLabel(state.kind)
}

@Composable
private fun detailMetadata(state: TelevisionDetailState): List<String> {
    val item = state.item ?: return emptyList()
    val metadata = mutableListOf<String>()
    when (state.kind) {
        DetailKind.Series -> {
            item.productionYear?.let { metadata += it.toString() }
            val episodeCount = item.episodeCount?.takeIf { it > 0 }
            if (episodeCount != null) {
                metadata += pluralStringResource(R.plurals.tv_episodes_count, episodeCount, episodeCount)
            }
            val quality = playbackQualityLabel(state.playbackItem?.mediaStreams.orEmpty())
            if (quality != null) metadata += quality
            val genre = item.genres.firstOrNull()?.takeIf(String::isNotBlank)
            if (genre != null) metadata += genre
            val rating = item.officialRating?.takeIf(String::isNotBlank)
            if (rating != null) metadata += rating
        }

        DetailKind.Episode -> {
            if (item.runTimeTicks?.let { it > 0 } == true) {
                state.hero?.runtimeLabel?.let { metadata += it }
            }
            val date = detailDateLabel(item.premiereDate)
            if (date != null) metadata += date
            val rating = item.officialRating?.takeIf(String::isNotBlank)
            if (rating != null) metadata += rating
            val quality = playbackQualityLabel(state.playbackItem?.mediaStreams.orEmpty())
            if (quality != null) metadata += quality
        }

        else -> metadata += state.hero?.metadata.orEmpty()
    }
    return metadata
}

@Composable
private fun detailPlayLabel(state: TelevisionDetailState): String {
    val action = stringResource(
        if (state.playableResumeTicks > 0) R.string.resume else R.string.play,
    )
    if (state.kind != DetailKind.Series) return action
    val nextUp = state.nextUp ?: return action
    return listOfNotNull(
        action,
        episodeLabel(nextUp.seasonNumber, nextUp.episodeNumber),
    ).joinToString(" ")
}

@Composable
private fun episodeLabel(seasonNumber: Int?, episodeNumber: Int?): String? =
    if (seasonNumber != null || episodeNumber != null) {
        stringResource(
            R.string.tv_season_episode,
            seasonNumber ?: 0,
            episodeNumber ?: 0,
        )
    } else {
        null
    }

@Composable
private fun playbackQualityLabel(streams: List<DetailMediaStream>): String? {
    val height = streams.firstOrNull { it.type.equals("Video", true) }?.height ?: return null
    return when {
        height >= 2160 -> stringResource(R.string.tv_resolution_4k)
        height >= 1080 -> stringResource(R.string.tv_resolution_1080p)
        height >= 720 -> stringResource(R.string.tv_resolution_720p)
        else -> stringResource(R.string.tv_resolution_height, height)
    }
}

@Composable
private fun detailKindLabel(kind: DetailKind): String = when (kind) {
    DetailKind.Film -> stringResource(R.string.tv_detail_movie)
    DetailKind.Series -> stringResource(R.string.tv_detail_series_kind)
    DetailKind.Episode -> stringResource(R.string.tv_episode)
    DetailKind.Album -> stringResource(R.string.tv_detail_album_kind)
    DetailKind.Artist -> stringResource(R.string.tv_detail_artist_kind)
    DetailKind.Playlist -> stringResource(R.string.tv_detail_playlist_kind)
    DetailKind.AudioBook -> stringResource(R.string.tv_detail_audiobook_kind)
    DetailKind.Collection -> stringResource(R.string.tv_detail_collection_kind)
    DetailKind.Person -> stringResource(R.string.tv_detail_person_kind)
    DetailKind.Live -> stringResource(R.string.tv_detail_live_kind)
    DetailKind.Generic -> stringResource(R.string.tv_detail_media_kind)
}

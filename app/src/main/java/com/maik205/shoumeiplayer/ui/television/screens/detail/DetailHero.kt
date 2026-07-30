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

private fun DetailMediaStream?.streamLabel(fallback: String): String {
    if (this == null) return fallback
    return displayTitle
        ?.takeIf(String::isNotBlank)
        ?: listOfNotNull(
            language?.uppercase()?.takeIf(String::isNotBlank),
            codec?.uppercase()?.takeIf(String::isNotBlank),
            channels?.let { "$it ch" },
        ).joinToString(" ").ifBlank { fallback }
}

internal fun detailQualityOptions(streams: List<DetailMediaStream>): List<String> {
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

private fun playbackQualityLabel(streams: List<DetailMediaStream>): String? {
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

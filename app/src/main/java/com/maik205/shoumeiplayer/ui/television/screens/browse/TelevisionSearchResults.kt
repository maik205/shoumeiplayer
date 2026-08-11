package com.maik205.shoumeiplayer.ui.television.screens.browse

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.model.ArtworkShape
import com.maik205.shoumeiplayer.domain.model.MediaItem
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import kotlinx.coroutines.delay

@Composable
internal fun TelevisionSearchResultCard(
    item: MediaItem,
    anyResultFocused: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = TelevisionTheme.colors
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        modifier = modifier.fillMaxWidth(),
        scaleTo = 1.045f,
        restingAlpha = if (anyResultFocused) 0.58f else 0.76f,
        focusedTranslationY = (-2).dp,
        focusAnimationMillis = 180,
        onFocusChanged = { if (it) onFocused() },
    ) { focused ->
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SearchArtworkStageHeight)
                    .clip(RoundedCornerShape(TelevisionDimensions.FocusRadius)),
                contentAlignment = Alignment.Center,
            ) {
                val presentation = item.searchArtworkPresentation()
                if (item.imageUrl != null) {
                    AsyncImage(
                        model = item.imageUrl,
                        contentDescription = item.title,
                        modifier = Modifier
                            .size(presentation.width, presentation.height)
                            .clip(presentation.shape),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(presentation.width, presentation.height)
                            .clip(presentation.shape)
                            .background(colors.ImagePlaceholder),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = item.searchPlaceholderIcon(),
                            contentDescription = null,
                            tint = colors.PaperSoft,
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }

                item.progress?.takeIf { it > 0f }?.let { progress ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(2.dp)
                            .background(colors.Ember),
                    )
                }
                if (item.watched) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = colors.Paper,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(7.dp)
                            .size(13.dp),
                    )
                }
            }

            Spacer(Modifier.height(7.dp))
            Text(
                text = item.searchCardTitle(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 10.sp,
                    lineHeight = 12.5.sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = if (focused) colors.Paper else colors.Paper.copy(alpha = 0.88f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = item.searchCardMetadata(),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                ),
                color = colors.PaperMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun TelevisionSearchInspector(
    item: MediaItem?,
    modifier: Modifier = Modifier,
) {
    if (item == null) return
    val colors = TelevisionTheme.colors
    var artworkItem by remember { mutableStateOf(item) }
    LaunchedEffect(item?.id) {
        if (artworkItem == null) {
            artworkItem = item
        } else {
            delay(SearchInspectorArtworkDelayMillis)
            artworkItem = item
        }
    }

    Column(modifier = modifier.width(SearchInspectorWidth)) {
        AnimatedContent(
            targetState = artworkItem,
            transitionSpec = {
                (slideInHorizontally(
                    animationSpec = tween(180),
                    initialOffsetX = { it / 7 },
                ) + fadeIn(tween(140))) togetherWith fadeOut(tween(100))
            },
            label = "searchInspectorArtwork",
        ) { artwork ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(SearchInspectorArtworkHeight)
                    .clip(RoundedCornerShape(TelevisionDimensions.FocusRadius))
                    .background(colors.ImagePlaceholder),
                contentAlignment = Alignment.Center,
            ) {
                val backdropUrl = artwork?.backdropUrl
                val primaryUrl = artwork?.imageUrl
                val previewUrl = backdropUrl ?: primaryUrl
                if (previewUrl != null && artwork != null) {
                    AsyncImage(
                        model = previewUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = 1.14f
                                scaleY = 1.14f
                                alpha = 0.30f
                            }
                            .blur(14.dp),
                        contentScale = ContentScale.Crop,
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.Black.copy(alpha = 0.34f)),
                    )
                    if (backdropUrl != null) {
                        AsyncImage(
                            model = previewUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                    } else {
                        val presentation = artwork.searchInspectorArtworkPresentation()
                        AsyncImage(
                            model = previewUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(presentation.width, presentation.height)
                                .clip(presentation.shape),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.38f to Color.Transparent,
                                1f to colors.Black.copy(alpha = 0.82f),
                            ),
                        ),
                )
            }
        }

        if (item != null) {
            Spacer(Modifier.height(17.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 27.sp,
                    lineHeight = 29.sp,
                    letterSpacing = (-1.1).sp,
                ),
                fontWeight = FontWeight.SemiBold,
                color = colors.Paper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = item.searchInspectorMetadata(),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                ),
                color = colors.PaperMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.overview?.takeIf(String::isNotBlank)?.let { overview ->
                Spacer(Modifier.height(13.dp))
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                    ),
                    color = colors.Paper.copy(alpha = 0.76f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MediaItem.searchCardMetadata(): String {
    if (type == "Episode") {
        return listOfNotNull(
            episodeIndexLabel(),
            title.takeIf { seriesName != null },
        ).joinToString("  ·  ").ifBlank { searchTypeLabel() }
    }
    return listOfNotNull(
        searchTypeLabel(),
        premiereDate?.take(4)?.takeIf { it.all(Char::isDigit) },
        subtitle?.takeIf(String::isNotBlank),
    ).distinct().take(2).joinToString("  ·  ")
}

@Composable
private fun MediaItem.searchInspectorMetadata(): String = listOfNotNull(
    seriesName?.takeIf { type == "Episode" },
    episodeIndexLabel(),
    searchTypeLabel().takeIf { type != "Episode" },
    premiereDate?.take(4)?.takeIf { it.all(Char::isDigit) },
    runtimeLabel,
    officialRating,
).distinct().take(4).joinToString("  ·  ")

@Composable
private fun MediaItem.searchTypeLabel(): String = stringResource(
    when (type) {
        "Movie" -> R.string.tv_media_movie
        "Series" -> R.string.tv_media_series
        "Episode" -> R.string.tv_media_episode
        "MusicAlbum" -> R.string.tv_music_album
        "MusicArtist" -> R.string.tv_music_artist
        "Playlist" -> R.string.tv_music_playlist
        "Audio" -> R.string.tv_music_song
        else -> R.string.tv_media_item
    },
)

private fun MediaItem.searchCardTitle(): String =
    if (type == "Episode") seriesName?.takeIf(String::isNotBlank) ?: title else title

private fun MediaItem.episodeIndexLabel(): String? {
    val season = seasonNumber ?: return null
    val episode = episodeNumber ?: return null
    return "S$season E$episode"
}

private data class SearchArtworkPresentation(
    val width: Dp,
    val height: Dp,
    val shape: Shape,
)

private fun MediaItem.searchArtworkPresentation(): SearchArtworkPresentation = when {
    type == "MusicArtist" -> SearchArtworkPresentation(116.dp, 116.dp, CircleShape)
    shape == ArtworkShape.Square || type in setOf("MusicAlbum", "Playlist", "Audio") ->
        SearchArtworkPresentation(126.dp, 126.dp, RoundedCornerShape(TelevisionDimensions.FocusRadius))
    shape == ArtworkShape.Landscape || type == "Episode" ->
        SearchArtworkPresentation(130.dp, 74.dp, RoundedCornerShape(TelevisionDimensions.FocusRadius))
    else -> SearchArtworkPresentation(94.dp, 141.dp, RoundedCornerShape(TelevisionDimensions.FocusRadius))
}

private fun MediaItem.searchInspectorArtworkPresentation(): SearchArtworkPresentation = when {
    type == "MusicArtist" -> SearchArtworkPresentation(136.dp, 136.dp, CircleShape)
    shape == ArtworkShape.Square || type in setOf("MusicAlbum", "Playlist", "Audio") ->
        SearchArtworkPresentation(144.dp, 144.dp, RoundedCornerShape(TelevisionDimensions.FocusRadius))
    shape == ArtworkShape.Landscape || type == "Episode" ->
        SearchArtworkPresentation(SearchInspectorWidth, SearchInspectorArtworkHeight, RoundedCornerShape(TelevisionDimensions.FocusRadius))
    else -> SearchArtworkPresentation(96.dp, 144.dp, RoundedCornerShape(TelevisionDimensions.FocusRadius))
}

private fun MediaItem.searchPlaceholderIcon(): ImageVector = when (type) {
    "Movie" -> Icons.Default.Movie
    "Series", "Episode" -> Icons.Default.Tv
    "MusicAlbum" -> Icons.Default.Album
    "MusicArtist" -> Icons.Default.Person
    "Playlist" -> Icons.Default.PlaylistPlay
    "Audio" -> Icons.Default.MusicNote
    else -> Icons.Default.Movie
}

internal val SearchInspectorWidth = 270.dp
private val SearchArtworkStageHeight = 146.dp
private val SearchInspectorArtworkHeight = 150.dp
private const val SearchInspectorArtworkDelayMillis = 120L

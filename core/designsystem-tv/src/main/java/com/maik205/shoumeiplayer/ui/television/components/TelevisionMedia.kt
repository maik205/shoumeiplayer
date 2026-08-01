package com.maik205.shoumeiplayer.ui.television.components

import android.os.Build
import android.os.PowerManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maik205.shoumeiplayer.domain.model.ArtworkShape
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.core.designsystem.tv.R
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private const val ARTWORK_CROSSFADE_MS = 180
private const val ITEM_TITLE_SCALE = 0.70f
private const val ARTWORK_PREFETCH_COUNT = 2

fun TextStyle.televisionItemTitle(): TextStyle = copy(
    fontSize = fontSize.scaledItemTitleUnit(),
    lineHeight = lineHeight.scaledItemTitleUnit(),
)

private fun TextUnit.scaledItemTitleUnit(): TextUnit =
    if (this == TextUnit.Unspecified) this else (value * ITEM_TITLE_SCALE).sp

@Composable
fun TelevisionRowHeader(
    title: String,
    modifier: Modifier = Modifier,
    supportingText: String? = null,
    action: (@Composable RowScope.() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = TelevisionColors.Paper,
            )
            if (supportingText != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = TelevisionColors.PaperSoft,
                )
            }
        }
        action?.invoke(this)
    }
}

@Composable
fun TelevisionMediaTile(
    item: MediaItemUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: ArtworkShape = item.shape,
    focusRequester: FocusRequester? = null,
    onFocused: (MediaItemUi) -> Unit = {},
    bringIntoViewOnFocus: Boolean = true,
    tileWidth: Dp? = null,
    tileHeight: Dp? = null,
    artworkShape: Shape = RoundedCornerShape(TelevisionDimensions.FocusRadius),
    focusScale: Float? = null,
    restingAlpha: Float? = null,
    focusedTranslationY: Dp = 0.dp,
    focusAnimationMillis: Int = 120,
    titleStyle: TextStyle? = null,
    subtitleOverride: String? = null,
    subtitleStyle: TextStyle? = null,
    subtitleColor: Color = TelevisionColors.PaperMuted,
    showUnfocusedVeil: Boolean = true,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val defaultDimensions = shape.dimensions()
    val dimensions = TileDimensions(
        width = tileWidth ?: defaultDimensions.width,
        height = tileHeight ?: defaultDimensions.height,
    )
    val resolvedFocusScale = focusScale ?: when (shape) {
        ArtworkShape.Landscape -> TelevisionFocusScale.Landscape
        ArtworkShape.Square -> TelevisionFocusScale.Square
        ArtworkShape.Poster, ArtworkShape.Portrait -> TelevisionFocusScale.Poster
    }
    val resolvedTitleStyle = (titleStyle ?: MaterialTheme.typography.titleSmall).televisionItemTitle()

    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        modifier = modifier
            .width(dimensions.width)
            .televisionBringIntoViewOnFocus(enabled = bringIntoViewOnFocus),
        scaleTo = resolvedFocusScale,
        restingAlpha = restingAlpha ?: if (item.watched) 0.54f else 0.9f,
        focusedTranslationY = focusedTranslationY,
        focusAnimationMillis = focusAnimationMillis,
        onFocusChanged = {
            if (it) onFocused(item)
            onFocusChanged(it)
        },
    ) { focused ->
        Column {
            if (shape == ArtworkShape.Landscape) {
                Box(
                    modifier = Modifier
                        .size(dimensions.width, dimensions.height)
                        .clip(RoundedCornerShape(TelevisionDimensions.FocusRadius)),
                ) {
                    TelevisionArtwork(
                        imageUrl = item.imageUrl,
                        title = item.title,
                        progress = item.progress,
                        watched = item.watched,
                        focused = focused,
                        showUnfocusedVeil = showUnfocusedVeil,
                        artworkShape = artworkShape,
                        width = dimensions.width,
                        height = dimensions.height,
                    )
                    Box(
                        modifier = Modifier
                            .size(dimensions.width, dimensions.height)
                            .background(
                                Brush.verticalGradient(
                                    0.30f to TelevisionColors.Black.copy(alpha = 0f),
                                    1f to TelevisionColors.Black.copy(alpha = 0.84f),
                                ),
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 9.dp, end = 9.dp, bottom = 8.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = resolvedTitleStyle,
                            fontWeight = FontWeight.Bold,
                            color = TelevisionColors.Paper,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subtitle = item.subtitle
                        if (!subtitle.isNullOrBlank()) {
                            Spacer(Modifier.height(1.5.dp))
                            Text(
                                text = subtitle,
                                style = subtitleStyle ?: MaterialTheme.typography.bodySmall,
                                color = subtitleColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            } else {
                TelevisionArtwork(
                    imageUrl = item.imageUrl,
                    title = item.title,
                    progress = item.progress,
                    watched = item.watched,
                    focused = focused,
                    showUnfocusedVeil = showUnfocusedVeil,
                    artworkShape = artworkShape,
                    width = dimensions.width,
                    height = dimensions.height,
                )
                Spacer(Modifier.height(5.5.dp))
                Text(
                    text = item.title,
                    style = resolvedTitleStyle,
                    fontWeight = FontWeight.Bold,
                    color = TelevisionColors.Paper,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = subtitleOverride ?: item.subtitle
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = subtitleStyle ?: MaterialTheme.typography.bodySmall,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TelevisionArtwork(
    imageUrl: String?,
    title: String,
    progress: Float?,
    watched: Boolean,
    focused: Boolean,
    showUnfocusedVeil: Boolean,
    artworkShape: Shape,
    width: Dp,
    height: Dp,
) {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { width.roundToPx() }
    val heightPx = with(density) { height.roundToPx() }
    val request = remember(imageUrl, context, widthPx, heightPx) {
        imageUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .size(widthPx, heightPx)
                .crossfade(ARTWORK_CROSSFADE_MS)
                .build()
        }
    }

    Box(
        modifier = Modifier
            .size(width, height)
            .clip(artworkShape)
            .background(TelevisionColors.ImagePlaceholder),
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = title,
                modifier = Modifier.size(width, height),
                contentScale = ContentScale.Crop,
            )
        }
        if (!focused && showUnfocusedVeil) {
            Box(
                modifier = Modifier
                    .size(width, height)
                    .background(TelevisionColors.ImageVeil),
            )
        }
        TelevisionProgressMark(
            progress = progress,
            watched = watched,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
fun TelevisionArtworkPrefetch(
    items: List<MediaItemUi>,
    listState: LazyListState,
    tileWidth: Dp? = null,
    tileHeight: Dp? = null,
    shapeForItem: (MediaItemUi) -> ArtworkShape = { it.shape },
) {
    val context = LocalPlatformContext.current
    val density = LocalDensity.current
    val imageLoader = SingletonImageLoader.get(context)
    val powerManager = context.getSystemService(PowerManager::class.java)
    val currentShapeForItem by rememberUpdatedState(shapeForItem)

    LaunchedEffect(items, listState, tileWidth, tileHeight, density, imageLoader) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index to
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    powerManager?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE
                } else {
                    PowerManager.THERMAL_STATUS_NONE
                }
        }
            .distinctUntilChanged()
            .collectLatest { (lastVisibleIndex, thermalStatus) ->
                if (lastVisibleIndex == null) return@collectLatest
                if (!thermalAllowsOptionalWork(thermalStatus)) return@collectLatest
                coroutineScope {
                    artworkPrefetchIndices(lastVisibleIndex, items.size).forEach { index ->
                        launch {
                            val item = items[index]
                            val dimensions = currentShapeForItem(item).dimensions()
                            val widthPx = with(density) { (tileWidth ?: dimensions.width).roundToPx() }
                            val heightPx = with(density) { (tileHeight ?: dimensions.height).roundToPx() }
                            item.imageUrl?.let { imageUrl ->
                                imageLoader.execute(
                                    ImageRequest.Builder(context)
                                        .data(imageUrl)
                                        .size(widthPx, heightPx)
                                        .build(),
                                )
                            }
                        }
                    }
                }
            }
    }
}

internal fun thermalAllowsOptionalWork(status: Int): Boolean =
    status < PowerManager.THERMAL_STATUS_MODERATE

internal fun artworkPrefetchIndices(
    lastVisibleIndex: Int,
    itemCount: Int,
): IntRange {
    val firstPrefetchIndex = lastVisibleIndex + 1
    if (firstPrefetchIndex !in 0 until itemCount) return IntRange.EMPTY
    return firstPrefetchIndex..minOf(lastVisibleIndex + ARTWORK_PREFETCH_COUNT, itemCount - 1)
}

@Composable
fun TelevisionProgressMark(
    progress: Float?,
    watched: Boolean,
    modifier: Modifier = Modifier,
) {
    if (watched) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = stringResource(R.string.ds_watched),
                modifier = Modifier.size(18.dp),
                tint = TelevisionColors.Paper,
            )
        }
        return
    }

    val value = progress?.coerceIn(0f, 1f) ?: return
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(3.dp)
            .background(TelevisionColors.ProgressTrack),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(value)
                .height(3.dp)
                .background(TelevisionColors.Ember),
        )
    }
}

private data class TileDimensions(val width: Dp, val height: Dp)

private fun ArtworkShape.dimensions(): TileDimensions = when (this) {
    ArtworkShape.Landscape -> TileDimensions(
        TelevisionDimensions.LandscapeWidth,
        TelevisionDimensions.LandscapeHeight,
    )
    ArtworkShape.Square -> TileDimensions(
        TelevisionDimensions.SquareSize,
        TelevisionDimensions.SquareSize,
    )
    ArtworkShape.Poster, ArtworkShape.Portrait -> TileDimensions(
        TelevisionDimensions.PosterWidth,
        TelevisionDimensions.PosterHeight,
    )
}

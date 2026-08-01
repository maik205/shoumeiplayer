package com.maik205.shoumeiplayer.ui.television.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.model.DetailItem
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusScale
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.TelevisionMediaTile
import com.maik205.shoumeiplayer.ui.television.components.TelevisionProgressMark
import com.maik205.shoumeiplayer.ui.television.components.TelevisionRowHeader
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.televisionItemTitle
import com.maik205.shoumeiplayer.domain.model.ArtworkShape
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
internal fun DetailAbout(state: TelevisionDetailState) {
    val item = state.item ?: return
    val facts = when (state.kind) {
        DetailKind.Series -> buildList {
            item.people.firstOrNull {
                it.type.equals("Writer", ignoreCase = true) ||
                    it.type.equals("Producer", ignoreCase = true)
            }?.name?.let { add(R.string.tv_detail_created_by to it) }
            item.status?.let { add(R.string.tv_detail_status to it) }
            state.seasons.size.takeIf { it > 0 }?.let {
                add(R.string.tv_detail_seasons to it.toString())
            }
            item.episodeCount?.takeIf { it > 0 }?.let {
                add(R.string.tv_episodes to it.toString())
            }
            item.genres.takeIf { it.isNotEmpty() }?.let {
                add(R.string.tv_detail_genres to it.joinToString(", "))
            }
            item.officialRating?.let { add(R.string.tv_detail_rating to it) }
        }

        DetailKind.Episode -> buildList {
            item.people.firstOrNull {
                it.type.equals("Director", ignoreCase = true)
            }?.name?.let { add(R.string.tv_detail_director to it) }
            item.people.firstOrNull {
                it.type.equals("Writer", ignoreCase = true)
            }?.name?.let { add(R.string.tv_detail_writer to it) }
            item.seriesName?.let { add(R.string.tv_detail_series to it) }
            item.seasonName?.let { add(R.string.tv_detail_season to it) }
            detailDateLabel(item.premiereDate)?.let { add(R.string.tv_detail_release to it) }
            item.officialRating?.let { add(R.string.tv_detail_rating to it) }
        }

        else -> buildList {
            item.studios.firstOrNull()?.let { add(R.string.tv_detail_studio to it) }
            item.genres.takeIf { it.isNotEmpty() }?.let {
                add(R.string.tv_detail_genres to it.joinToString(", "))
            }
            item.officialRating?.let { add(R.string.tv_detail_rating to it) }
            item.communityRating?.let {
                add(R.string.tv_detail_community to String.format(Locale.getDefault(), "%.1f", it))
            }
            item.status?.let { add(R.string.tv_detail_status to it) }
        }
    }
    if (facts.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = TelevisionDimensions.SafeHorizontal,
                end = TelevisionDimensions.SafeHorizontal,
                top = 48.dp,
                bottom = 28.dp,
            ),
    ) {
        TelevisionRowHeader(title = stringResource(R.string.tv_detail_about))
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
            facts.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(42.dp)) {
                    row.forEach { (labelRes, value) ->
                        Column(modifier = Modifier.width(220.dp)) {
                            Text(
                                text = stringResource(labelRes),
                                style = MaterialTheme.typography.labelSmall,
                                color = TelevisionColors.PaperMuted,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

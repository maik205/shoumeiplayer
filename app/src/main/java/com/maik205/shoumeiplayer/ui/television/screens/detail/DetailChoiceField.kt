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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.domain.model.DetailItem
import com.maik205.shoumeiplayer.R
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
internal fun DetailChoiceField(
    label: String,
    options: List<String>,
    selectedIndex: Int,
    icon: ImageVector,
    onSelect: (Int) -> Unit,
    onFocused: () -> Unit = {},
) {
    val selectorFocus = remember { FocusRequester() }
    val selectedFocus = remember { FocusRequester() }
    val resolvedIndex = selectedIndex.takeIf { it in options.indices } ?: 0
    val value = if (resolvedIndex in options.indices) {
        options[resolvedIndex]
    } else {
        stringResource(R.string.tv_none)
    }
    val optionListState = rememberLazyListState(initialFirstVisibleItemIndex = resolvedIndex)
    var expanded by remember { mutableStateOf(false) }
    var openedOnce by remember { mutableStateOf(false) }

    LaunchedEffect(expanded, resolvedIndex) {
        if (expanded) {
            openedOnce = true
            optionListState.scrollToItem(resolvedIndex)
            withFrameNanos { }
            runCatching { selectedFocus.requestFocus() }
        } else if (openedOnce) {
            runCatching { selectorFocus.requestFocus() }
        }
    }
    BackHandler(enabled = expanded) { expanded = false }

    TelevisionFocusSurface(
        onClick = { expanded = true },
        focusRequester = selectorFocus,
        scaleTo = TelevisionFocusScale.Navigation,
        restingAlpha = 0.54f,
        onFocusChanged = { if (it) onFocused() },
        modifier = Modifier
            .width(184.dp)
            .height(48.dp)
            .televisionBringIntoViewOnFocus(),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = TelevisionColors.PaperMuted,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.tv_change_selection, label),
                modifier = Modifier.size(19.dp),
            )
        }
    }

    if (expanded) {
        Popup(
            alignment = Alignment.CenterEnd,
            onDismissRequest = { expanded = false },
            properties = PopupProperties(focusable = true),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            0f to TelevisionColors.Black.copy(alpha = 0f),
                            0.58f to TelevisionColors.Black.copy(alpha = 0.74f),
                            1f to TelevisionColors.Black,
                        ),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(330.dp)
                        .background(
                            Brush.horizontalGradient(
                                0f to TelevisionColors.Black.copy(alpha = 0f),
                                0.20f to TelevisionColors.Black.copy(alpha = 0.96f),
                                1f to TelevisionColors.Black,
                            ),
                        )
                        .focusProperties { onExit = { cancelFocusChange() } }
                        .focusGroup()
                        .padding(
                            start = 72.dp,
                            end = TelevisionDimensions.SafeHorizontal,
                            top = 72.dp,
                            bottom = TelevisionDimensions.SafeBottom,
                        ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        state = optionListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(
                            items = options,
                            key = { index, option -> "$index:$option" },
                        ) { index, option ->
                            val isSelected = index == resolvedIndex
                            TelevisionFocusSurface(
                                onClick = {
                                    onSelect(index)
                                    expanded = false
                                },
                                focusRequester = if (isSelected) selectedFocus else null,
                                restingAlpha = if (isSelected) 1f else 0.54f,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .televisionBringIntoViewOnFocus(),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = option,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = stringResource(R.string.tv_selected),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

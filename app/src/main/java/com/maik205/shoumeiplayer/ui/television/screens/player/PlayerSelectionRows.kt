package com.maik205.shoumeiplayer.ui.television.screens.player

import android.view.KeyEvent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.player.PlaybackSpeed
import com.maik205.shoumeiplayer.player.PlayerTrack
import com.maik205.shoumeiplayer.player.VideoQuality
import com.maik205.shoumeiplayer.feature.player.PlayerShelfItem
import com.maik205.shoumeiplayer.feature.player.CastMemberUi
import com.maik205.shoumeiplayer.feature.player.ChapterMark
import com.maik205.shoumeiplayer.feature.player.PlayerUiState
import com.maik205.shoumeiplayer.feature.player.UpNextUi
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.components.televisionItemTitle
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import java.util.Locale

internal fun audioRows(
    tracks: List<PlayerTrack>,
    onSelect: (PlayerTrack) -> Unit,
): List<PlayerSelectionRow> = tracks.map { track ->
    val language = displayTrackLanguage(track.language)
    val label = track.label
        .takeUnless { it.isBlank() || it.equals(track.language, ignoreCase = true) }
        ?: language
    PlayerSelectionRow(
        key = "audio:${track.id}",
        label = label,
        detail = language.takeUnless { it.equals(label, ignoreCase = true) },
        selected = track.selected,
        onClick = { onSelect(track) },
    )
}

internal fun subtitleRows(
    tracks: List<PlayerTrack>,
    onSelect: (PlayerTrack) -> Unit,
): List<PlayerSelectionRow> = tracks.map { track ->
    val language = displayTrackLanguage(track.language)
    val label = track.label
        .takeUnless { it.isBlank() || it.equals(track.language, ignoreCase = true) }
        ?: language
    PlayerSelectionRow(
        key = "subtitle:${track.id}",
        label = label,
        detail = language.takeUnless { it.equals(label, ignoreCase = true) },
        selected = track.selected,
        onClick = { onSelect(track) },
    )
}

internal fun chapterRows(
    chapters: List<ChapterMark>,
    onSelect: (ChapterMark) -> Unit,
): List<PlayerSelectionRow> = chapters.mapIndexed { index, chapter ->
    PlayerSelectionRow(
        key = "chapter:${chapter.positionMs}",
        label = chapter.name?.takeIf(String::isNotBlank) ?: "Chapter ${index + 1}",
        detail = formatPlayerTime(chapter.positionMs),
        onClick = { onSelect(chapter) },
    )
}

private fun displayTrackLanguage(language: String?): String {
    val value = language?.trim().orEmpty()
    return when (value.lowercase(Locale.ROOT)) {
        "ja", "jpn" -> "Japanese"
        "en", "eng" -> "English"
        "vi", "vie" -> "Vietnamese"
        "und", "" -> "Unknown language"
        else -> value
    }
}

internal fun qualityRows(
    selected: VideoQuality,
    onSelect: (VideoQuality) -> Unit,
): List<PlayerSelectionRow> = VideoQuality.Ladder.map { quality ->
    PlayerSelectionRow(
        key = "quality:${quality.name}",
        label = quality.label,
        detail = if (quality == VideoQuality.AUTO) "Prefer direct play" else "Maximum stream quality",
        selected = quality == selected,
        onClick = { onSelect(quality) },
    )
}

internal fun speedRows(
    selected: Float,
    onSelect: (Float) -> Unit,
): List<PlayerSelectionRow> = PlaybackSpeed.Steps.map { speed ->
    PlayerSelectionRow(
        key = "speed:$speed",
        label = PlaybackSpeed.label(speed),
        selected = PlaybackSpeed.nearestStep(selected) == speed,
        onClick = { onSelect(speed) },
    )
}

internal fun formatPlayerTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}

internal fun signedDelay(valueMs: Long): String = when {
    valueMs > 0L -> "+${valueMs} ms"
    valueMs < 0L -> "${valueMs} ms"
    else -> "0 ms"
}

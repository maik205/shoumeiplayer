package com.maik205.shoumeiplayer.ui.television.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.ui.television.model.LibraryDestinationUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import kotlinx.coroutines.delay

@Immutable
data class TelevisionNavigationItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

fun televisionLibraryNavigationKey(libraryId: String): String = "library:$libraryId"

private val TelevisionPrimaryNavigation = listOf(
    TelevisionNavigationItem("search", "Search", Icons.Default.Search),
    TelevisionNavigationItem("home", "Home", Icons.Default.Home),
)

private const val LibraryFocusNavigationDelayMillis = 250L

@Composable
fun TelevisionAppTopNavigation(
    libraries: List<LibraryDestinationUi>,
    selectedKey: String?,
    userName: String,
    avatarUrl: String? = null,
    onNavigateHome: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateLibrary: (LibraryDestinationUi) -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateProfile: () -> Unit,
    modifier: Modifier = Modifier,
    settingsFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    selectedFocusRequester: FocusRequester? = null,
) {
    TelevisionTopNavigation(
        primaryDestinations = TelevisionPrimaryNavigation,
        libraryDestinations = libraries,
        selectedKey = selectedKey,
        onDestinationClick = { key ->
            when (key) {
                "home" -> onNavigateHome()
                "search" -> onNavigateSearch()
                else -> if (key.startsWith("library:")) {
                    libraries
                        .firstOrNull { televisionLibraryNavigationKey(it.id) == key }
                        ?.let(onNavigateLibrary)
                }
            }
        },
        onSettingsClick = onNavigateSettings,
        onAvatarClick = onNavigateProfile,
        modifier = modifier,
        settingsFocusRequester = settingsFocusRequester,
        contentFocusRequester = contentFocusRequester,
        selectedFocusRequester = selectedFocusRequester,
        avatarUrl = avatarUrl,
        avatarLabel = userName.ifBlank { "Profile" },
        avatarInitials = userName.take(2).uppercase(),
    )
}

/**
 * Flat top navigation. Libraries are destinations, not a submenu, and are supplied from the live
 * Jellyfin library list.
 */
@Composable
fun TelevisionTopNavigation(
    primaryDestinations: List<TelevisionNavigationItem>,
    libraryDestinations: List<LibraryDestinationUi>,
    selectedKey: String?,
    onDestinationClick: (key: String) -> Unit,
    onSettingsClick: () -> Unit,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    avatarLabel: String = "Profile",
    avatarInitials: String = "",
    settingsFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    selectedFocusRequester: FocusRequester? = null,
) {
    // Restored focus is not a new tab choice. Only user-library tabs invoke navigation on focus,
    // and only after the user has rested there briefly.
    var previousFocusedKey by remember { mutableStateOf<String?>(null) }
    var focusedKey by remember { mutableStateOf<String?>(null) }
    var pendingLibraryKey by remember { mutableStateOf<String?>(null) }
    fun onNavigationFocusChanged(key: String, library: Boolean, focused: Boolean) {
        if (!focused) {
            if (focusedKey == key) {
                focusedKey = null
                pendingLibraryKey = null
            }
            return
        }

        val previousKey = previousFocusedKey
        previousFocusedKey = key
        focusedKey = key
        pendingLibraryKey = key.takeIf {
            library && previousKey != null && previousKey != key && selectedKey != key
        }
    }
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }
    val resolvedSettingsFocusRequester = when {
        selectedKey == "settings" && selectedFocusRequester != null -> selectedFocusRequester
        settingsFocusRequester != null -> settingsFocusRequester
        else -> focusRequesters.getOrPut("settings") { FocusRequester() }
    }
    focusRequesters["settings"] = resolvedSettingsFocusRequester
    fun focusRequesterFor(key: String): FocusRequester =
        selectedFocusRequester?.takeIf { key == selectedKey }
            ?: focusRequesters.getOrPut(key) { FocusRequester() }

    // After a tab changes, restore the remote to that tab in the navbar. Route composition must
    // not send it down to the new screen's first content target.
    LaunchedEffect(selectedKey, libraryDestinations) {
        selectedKey?.let { key ->
            (selectedFocusRequester?.takeIf { key == selectedKey } ?: focusRequesters[key])
                ?.requestFocus()
        }
    }
    LaunchedEffect(pendingLibraryKey) {
        val key = pendingLibraryKey ?: return@LaunchedEffect
        delay(LibraryFocusNavigationDelayMillis)
        if (focusedKey == key && selectedKey != key) onDestinationClick(key)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = TelevisionDimensions.SafeHorizontal,
                end = TelevisionDimensions.SafeHorizontal,
                top = TelevisionDimensions.NavigationTop,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            modifier = Modifier
                .weight(1f)
                .height(TelevisionDimensions.NavigationHeight)
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(TelevisionDimensions.NavigationGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items(primaryDestinations, key = { it.key }) { destination ->
                TelevisionTopNavigationItem(
                    label = destination.label,
                    icon = destination.icon,
                    selected = selectedKey == destination.key,
                    onClick = { onDestinationClick(destination.key) },
                    onFocusChanged = { focused ->
                        onNavigationFocusChanged(destination.key, library = false, focused = focused)
                    },
                    focusRequester = focusRequesterFor(destination.key),
                    modifier = Modifier
                        .then(
                            contentFocusRequester?.let { target ->
                                Modifier.focusProperties { down = target }
                            } ?: Modifier,
                        )
                        .televisionBringIntoViewOnFocus(),
                )
            }
            items(libraryDestinations, key = { it.id }) { library ->
                val key = televisionLibraryNavigationKey(library.id)
                TelevisionTopNavigationItem(
                    label = library.title,
                    icon = libraryNavigationIcon(library.collectionType),
                    selected = selectedKey == key,
                    onClick = { onDestinationClick(key) },
                    onFocusChanged = { focused ->
                        onNavigationFocusChanged(key, library = true, focused = focused)
                    },
                    focusRequester = focusRequesterFor(key),
                    modifier = Modifier
                        .then(
                            contentFocusRequester?.let { target ->
                                Modifier.focusProperties { down = target }
                            } ?: Modifier,
                        )
                        .televisionBringIntoViewOnFocus(),
                )
            }
        }

        Spacer(Modifier.width(8.dp))
        TelevisionFocusRevealButton(
            label = "Settings",
            icon = Icons.Default.Settings,
            selected = selectedKey == "settings",
            onClick = onSettingsClick,
            focusRequester = resolvedSettingsFocusRequester,
            expandedWidth = 64.dp,
            onFocusChanged = { focused ->
                onNavigationFocusChanged("settings", library = false, focused = focused)
            },
            modifier = contentFocusRequester?.let { target ->
                Modifier.focusProperties { down = target }
            } ?: Modifier,
        )
        Spacer(Modifier.width(6.dp))
        TelevisionAvatarButton(
            imageUrl = avatarUrl,
            label = avatarLabel,
            initials = avatarInitials,
            onClick = onAvatarClick,
            onFocused = { onNavigationFocusChanged("profile", library = false, focused = true) },
            contentFocusRequester = contentFocusRequester,
        )
    }
}

@Composable
private fun TelevisionTopNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        modifier = modifier.height(26.dp),
        scaleTo = TelevisionFocusScale.Navigation,
        restingAlpha = if (selected) 1f else 0.52f,
        onFocusChanged = onFocusChanged,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier
                    .height(22.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                androidx.tv.material3.Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(10.5.dp),
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .width(16.dp)
                        .height(1.dp)
                        .background(TelevisionColors.Paper),
                )
            }
        }
    }
}

private fun libraryNavigationIcon(collectionType: String?): ImageVector = when (
    collectionType?.lowercase()
) {
    "movies" -> Icons.Default.Movie
    "tvshows" -> Icons.Default.Tv
    "music" -> Icons.Default.MusicNote
    "livetv" -> Icons.Default.LiveTv
    "books" -> Icons.AutoMirrored.Filled.MenuBook
    "boxsets" -> Icons.Default.CollectionsBookmark
    "playlists" -> Icons.AutoMirrored.Filled.QueueMusic
    else -> Icons.Default.VideoLibrary
}

@Composable
private fun TelevisionAvatarButton(
    imageUrl: String?,
    label: String,
    initials: String,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    contentFocusRequester: FocusRequester? = null,
) {
    TelevisionFocusSurface(
        onClick = onClick,
        modifier = Modifier
            .size(22.dp)
            .then(
                contentFocusRequester?.let { target ->
                    Modifier.focusProperties { down = target }
                } ?: Modifier,
            ),
        scaleTo = TelevisionFocusScale.Navigation,
        restingAlpha = 0.72f,
        onFocusChanged = { focused -> if (focused) onFocused() },
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = label,
                modifier = Modifier
                    .size(21.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(21.dp)
                    .clip(CircleShape)
                    .background(TelevisionColors.Paper.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials.take(2).ifBlank { "S" },
                    style = MaterialTheme.typography.labelMedium,
                    color = TelevisionColors.Paper,
                )
            }
        }
    }
}

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.core.designsystem.tv.R
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

@Immutable
data class TelevisionNavigationItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

fun televisionLibraryNavigationKey(libraryId: String): String = "library:$libraryId"

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
    navigationState: LazyListState? = null,
    onNavigationFocused: () -> Unit = {},
) {
    val primaryDestinations = listOf(
        TelevisionNavigationItem("search", stringResource(R.string.ds_nav_search), Icons.Default.Search),
        TelevisionNavigationItem("home", stringResource(R.string.ds_nav_home), Icons.Default.Home),
    )
    val profileLabel = if (userName.isBlank()) {
        stringResource(R.string.ds_nav_profile)
    } else {
        userName
    }
    val avatarInitials = if (userName.isBlank()) {
        stringResource(R.string.ds_nav_initial)
    } else {
        userName.take(2).uppercase()
    }
    TelevisionTopNavigation(
        primaryDestinations = primaryDestinations,
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
        navigationState = navigationState,
        onNavigationFocused = onNavigationFocused,
        avatarUrl = avatarUrl,
        avatarLabel = profileLabel,
        avatarInitials = avatarInitials,
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
    avatarLabel: String,
    avatarInitials: String = "",
    settingsFocusRequester: FocusRequester? = null,
    contentFocusRequester: FocusRequester? = null,
    selectedFocusRequester: FocusRequester? = null,
    navigationState: LazyListState? = null,
    onNavigationFocused: () -> Unit = {},
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
        onNavigationFocused()
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
    val profileFocusRequester = focusRequesterFor("profile")
    val navigationFocusRequesters =
        primaryDestinations.map { focusRequesterFor(it.key) } +
            libraryDestinations.map { focusRequesterFor(televisionLibraryNavigationKey(it.id)) } +
            resolvedSettingsFocusRequester +
            profileFocusRequester
    val lazyNavigationItemCount = primaryDestinations.size + libraryDestinations.size
    val navigationRailState = navigationState ?: rememberLazyListState()
    val activeKeys = remember(primaryDestinations, libraryDestinations) {
        buildSet {
            primaryDestinations.forEach { add(it.key) }
            libraryDestinations.forEach { add(televisionLibraryNavigationKey(it.id)) }
            add("settings")
            add("profile")
        }
    }
    SideEffect {
        focusRequesters.keys.retainAll(activeKeys)
    }
    val selectedDestinationAvailable = selectedKey != null && selectedKey in activeKeys
    var initialFocusAssigned by rememberSaveable(selectedKey) { mutableStateOf(false) }

    // The navigation owns focus only on first entry to this back-stack destination. Content owns
    // restoration when the destination resumes after a detail screen.
    LaunchedEffect(selectedKey, selectedDestinationAvailable, initialFocusAssigned) {
        val key = selectedKey?.takeIf { selectedDestinationAvailable && !initialFocusAssigned }
            ?: return@LaunchedEffect
        val targetIndex = when (key) {
            "settings" -> lazyNavigationItemCount
            "profile" -> lazyNavigationItemCount + 1
            else -> primaryDestinations.indexOfFirst { it.key == key }
                .takeIf { it >= 0 }
                ?: libraryDestinations.indexOfFirst {
                    televisionLibraryNavigationKey(it.id) == key
                }.takeIf { it >= 0 }?.plus(primaryDestinations.size)
                ?: return@LaunchedEffect
        }
        if (targetIndex < lazyNavigationItemCount) {
            if (navigationRailState.layoutInfo.visibleItemsInfo.none { it.index == targetIndex }) {
                navigationRailState.scrollToItem(targetIndex)
            }
            snapshotFlow {
                navigationRailState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
            }.first { it }
        }
        // Resolve this exactly as the rendered item does. A destination screen can own the
        // selected requester's lifecycle; looking only in the internal map then targets a stale
        // or unattached requester and leaves focus on the reused first nav item (usually Search).
        val requester = focusRequesterFor(key)
        while (isActive && !runCatching { requester.requestFocus() }.getOrDefault(false)) {
            withFrameNanos { }
        }
        if (isActive) initialFocusAssigned = true
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
            state = navigationRailState,
            modifier = Modifier
                .weight(1f)
                .height(TelevisionDimensions.NavigationHeight)
                .focusGroup(),
            horizontalArrangement = Arrangement.spacedBy(TelevisionDimensions.NavigationGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(
                primaryDestinations,
                key = { _, destination -> destination.key },
            ) { index, destination ->
                TelevisionTopNavigationItem(
                    label = destination.label,
                    icon = destination.icon,
                    selected = selectedKey == destination.key,
                    pending = false,
                    onClick = { onDestinationClick(destination.key) },
                    onFocusChanged = { focused ->
                        onNavigationFocusChanged(destination.key, library = false, focused = focused)
                    },
                    focusRequester = focusRequesterFor(destination.key),
                    modifier = Modifier
                        .televisionNavigationRing(
                            index,
                            navigationFocusRequesters,
                            lazyNavigationItemCount,
                            navigationRailState,
                        )
                        .then(
                            contentFocusRequester?.let { target ->
                                Modifier.focusProperties { down = target }
                            } ?: Modifier,
                        )
                        .televisionBringIntoViewOnFocus(),
                )
            }
            itemsIndexed(
                libraryDestinations,
                key = { _, library -> library.id },
            ) { libraryIndex, library ->
                val key = televisionLibraryNavigationKey(library.id)
                val railIndex = primaryDestinations.size + libraryIndex
                TelevisionTopNavigationItem(
                    label = library.title,
                    icon = libraryNavigationIcon(library.collectionType),
                    selected = selectedKey == key,
                    pending = pendingLibraryKey == key,
                    onClick = { onDestinationClick(key) },
                    onFocusChanged = { focused ->
                        onNavigationFocusChanged(key, library = true, focused = focused)
                    },
                    focusRequester = focusRequesterFor(key),
                    modifier = Modifier
                        .televisionNavigationRing(
                            railIndex,
                            navigationFocusRequesters,
                            lazyNavigationItemCount,
                            navigationRailState,
                        )
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
            label = stringResource(R.string.ds_nav_settings),
            icon = Icons.Default.Settings,
            selected = selectedKey == "settings",
            onClick = onSettingsClick,
            focusRequester = resolvedSettingsFocusRequester,
            expandedWidth = 64.dp,
            onFocusChanged = { focused ->
                onNavigationFocusChanged("settings", library = false, focused = focused)
            },
            modifier = Modifier
                .televisionNavigationRing(
                    lazyNavigationItemCount,
                    navigationFocusRequesters,
                    lazyNavigationItemCount,
                    navigationRailState,
                )
                .then(
                    contentFocusRequester?.let { target ->
                        Modifier.focusProperties { down = target }
                    } ?: Modifier,
                ),
        )
        Spacer(Modifier.width(6.dp))
        TelevisionAvatarButton(
            imageUrl = avatarUrl,
            label = avatarLabel,
            initials = avatarInitials,
            onClick = onAvatarClick,
            focusRequester = profileFocusRequester,
            onFocusChanged = { focused ->
                onNavigationFocusChanged("profile", library = false, focused = focused)
            },
            contentFocusRequester = contentFocusRequester,
            modifier = Modifier.televisionNavigationRing(
                lazyNavigationItemCount + 1,
                navigationFocusRequesters,
                lazyNavigationItemCount,
                navigationRailState,
            ),
        )
    }
}

@Composable
private fun TelevisionTopNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    pending: Boolean,
    onClick: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = TelevisionTheme.colors
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        modifier = modifier.height(26.dp),
        scaleTo = TelevisionFocusScale.Navigation,
        restingAlpha = if (selected) 1f else 0.52f,
        onFocusChanged = onFocusChanged,
    ) { focused ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                modifier = Modifier
                    .height(22.dp)
                    .clip(RoundedCornerShape(TelevisionDimensions.FocusRadius))
                    .background(
                        if (focused) {
                            colors.Paper.copy(alpha = 0.12f)
                        } else {
                            androidx.compose.ui.graphics.Color.Transparent
                        },
                    )
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
                    modifier = Modifier.widthIn(max = 104.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (selected || pending) {
                Box(
                    modifier = Modifier
                        .width(if (selected) 16.dp else 8.dp)
                        .height(1.dp)
                        .background(
                            if (selected) {
                                colors.Paper
                            } else {
                                colors.PaperMuted
                            },
                        ),
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
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    contentFocusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier,
) {
    val colors = TelevisionTheme.colors
    TelevisionFocusSurface(
        onClick = onClick,
        focusRequester = focusRequester,
        modifier = modifier
            .size(22.dp)
            .then(
                contentFocusRequester?.let { target ->
                    Modifier.focusProperties { down = target }
                } ?: Modifier,
            ),
        scaleTo = TelevisionFocusScale.Navigation,
        restingAlpha = 0.72f,
        onFocusChanged = onFocusChanged,
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
                    .background(colors.Paper.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (initials.isBlank()) {
                        stringResource(R.string.ds_nav_initial)
                    } else {
                        initials.take(2)
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.Paper,
                )
            }
        }
    }
}

/**
 * Treats the scrollable destinations and the fixed Settings/Profile controls as one logical ring.
 * Lazy targets are brought into composition before focus moves; fixed targets are always attached.
 */
@Composable
private fun Modifier.televisionNavigationRing(
    index: Int,
    focusRequesters: List<FocusRequester>,
    lazyItemCount: Int,
    listState: LazyListState,
): Modifier {
    if (focusRequesters.size < 2 || index !in focusRequesters.indices) return this
    val scope = rememberCoroutineScope()
    var movementJob by remember { mutableStateOf<Job?>(null) }
    DisposableEffect(Unit) {
        onDispose { movementJob?.cancel() }
    }
    return onPreviewKeyEvent { event ->
        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
        val target = when (event.key) {
            Key.DirectionLeft -> if (index == 0) focusRequesters.lastIndex else index - 1
            Key.DirectionRight -> if (index == focusRequesters.lastIndex) 0 else index + 1
            else -> return@onPreviewKeyEvent false
        }

        movementJob?.cancel()
        movementJob = scope.launch {
            val lazyTargetIsVisible = target < lazyItemCount &&
                listState.layoutInfo.visibleItemsInfo.any { it.index == target }
            if (target < lazyItemCount && !lazyTargetIsVisible) {
                listState.scrollToItem(target)
                snapshotFlow {
                    listState.layoutInfo.visibleItemsInfo.any { it.index == target }
                }.first { it }
            }
            while (isActive && !runCatching {
                    focusRequesters[target].requestFocus()
                }.getOrDefault(false)
            ) {
                withFrameNanos { }
            }
        }
        true
    }
}

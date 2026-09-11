package com.maik205.shoumeiplayer.ui.television.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.di.LocalAppContainer
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.ui.television.components.TelevisionAppTopNavigation
import com.maik205.shoumeiplayer.ui.television.components.TelevisionErrorState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusScale
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.televisionBringIntoViewOnFocus
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
import com.maik205.shoumeiplayer.ui.i18n.UiText
import com.maik205.shoumeiplayer.ui.i18n.resolve
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme

@Composable
fun TelevisionSettingsScreen(
    state: TelevisionSettingsState,
    libraries: List<LibraryDestinationUi>,
    avatarUrl: String?,
    onUpdate: ((ClientSettings) -> ClientSettings) -> Unit,
    onSignOut: () -> Unit,
    onChangeServer: () -> Unit,
    onForgetServer: () -> Unit,
    onSwitchProfile: () -> Unit,
    onTestConnection: () -> Unit,
    onRefreshLibraries: () -> Unit,
    libraryRefreshing: Boolean,
    libraryRefreshError: UiText?,
    onRetrySettings: () -> Unit,
    onQuickConnect: () -> Unit,
    onClearArtworkCache: () -> Unit,
    onNavigateHome: () -> Unit,
    onNavigateSearch: () -> Unit,
    onNavigateLibrary: (LibraryDestinationUi) -> Unit,
    onNavigateProfile: () -> Unit,
    navigationState: LazyListState,
) {
    var section by remember { mutableStateOf(SettingsSection.Playback) }
    var activeChoice by remember { mutableStateOf<SettingRowModel?>(null) }
    var choiceReturnFocus by remember { mutableStateOf<FocusRequester?>(null) }
    val settingsTopFocus = remember { FocusRequester() }
    val sectionFocus = remember {
        SettingsSection.entries.associateWith { FocusRequester() }
    }
    val sectionRailFocus = SettingsSection.entries.map(sectionFocus::getValue)
    val sectionRailState = rememberLazyListState()

    LaunchedEffect(activeChoice) {
        if (activeChoice == null) {
            choiceReturnFocus?.let { requester ->
                runCatching { requester.requestFocus() }
            }
            choiceReturnFocus = null
        }
    }

    BackHandler(enabled = activeChoice != null) {
        activeChoice = null
    }

    val colors = TelevisionTheme.colors
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(colors.LibraryBackground)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            colors.Paper.copy(alpha = 0.07f),
                            androidx.compose.ui.graphics.Color.Transparent,
                        ),
                        center = Offset(size.width * 0.16f, 0f),
                        radius = size.width * 0.58f,
                    ),
                )
            },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    end = TelevisionDimensions.SafeHorizontal,
                    top = 50.dp,
                ),
        ) {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.displayMedium,
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(
                state = sectionRailState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .focusGroup()
                    .focusRestorer(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(horizontal = 2.dp),
            ) {
                itemsIndexed(
                    SettingsSection.entries,
                    key = { _, destination -> destination.name },
                ) { index, destination ->
                    TelevisionFocusSurface(
                        onClick = { section = destination },
                        restingAlpha = if (section == destination) 0.82f else 0.4f,
                        scaleTo = TelevisionFocusScale.Navigation,
                        focusRequester = sectionFocus.getValue(destination),
                        onFocusChanged = { focused ->
                            if (focused) {
                                section = destination
                            }
                        },
                        modifier = Modifier
                            .height(30.dp)
                            .televisionHorizontalWrap(index, sectionRailFocus, sectionRailState)
                            .focusProperties { up = settingsTopFocus }
                            .televisionBringIntoViewOnFocus(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = stringResource(destination.labelRes),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            val settingsError = state.error
            if (settingsError != null) {
                TelevisionErrorState(
                    title = stringResource(R.string.tv_settings_error_title),
                    message = settingsError.resolve(),
                    onRetry = onRetrySettings,
                    retryLabel = stringResource(R.string.retry),
                    requestInitialFocus = false,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (libraryRefreshError != null) {
                TelevisionErrorState(
                    title = stringResource(R.string.tv_settings_libraries_error_title),
                    message = libraryRefreshError.resolve(),
                    onRetry = onRefreshLibraries,
                    retryLabel = stringResource(R.string.retry),
                    requestInitialFocus = false,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            if (libraryRefreshing) {
                Text(
                    text = stringResource(R.string.tv_refreshing),
                    style = MaterialTheme.typography.bodySmall,
                    color = TelevisionTheme.colors.PaperMuted,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            SettingsSectionContent(
                section = section,
                rows = settingsRows(
                    section = section,
                    state = state,
                    update = onUpdate,
                    onSignOut = onSignOut,
                    onChangeServer = onChangeServer,
                    onForgetServer = onForgetServer,
                    onSwitchProfile = onSwitchProfile,
                    onTestConnection = onTestConnection,
                    onRefreshLibraries = onRefreshLibraries,
                    onQuickConnect = onQuickConnect,
                    onClearArtworkCache = onClearArtworkCache,
                ),
                selectedSectionFocus = sectionFocus.getValue(section),
                onOpenChoice = { row, returnFocus ->
                    choiceReturnFocus = returnFocus
                    activeChoice = row
                },
                onMoveSection = { offset ->
                    val destination = SettingsSection.entries.getOrNull(section.ordinal + offset)
                    destination?.let { target ->
                        runCatching { sectionFocus.getValue(target).requestFocus() }
                    }
                },
                modifier = Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .fillMaxHeight(),
            )
        }

        val container = LocalAppContainer.current
        val isCardVisible by container.remoteCoordinator.isCardVisible.collectAsState()
        val connectedClients by container.remoteCoordinator.connectedClients.collectAsState()

        TelevisionAppTopNavigation(
            libraries = libraries,
            selectedKey = "settings",
            userName = state.userName,
            avatarUrl = avatarUrl,
            onNavigateHome = onNavigateHome,
            onNavigateSearch = onNavigateSearch,
            onNavigateLibrary = onNavigateLibrary,
            onNavigateSettings = {},
            onNavigateProfile = onNavigateProfile,
            onPairClick = {
                container.remoteCoordinator.togglePairingCard()
            },
            isPairingActive = isCardVisible || connectedClients.isNotEmpty(),
            settingsFocusRequester = settingsTopFocus,
            contentFocusRequester = sectionFocus.getValue(section),
            navigationState = navigationState,
        )

        val choice = activeChoice
        if (choice != null) {
            SettingsChoiceDrawer(
                row = choice,
                onSelect = { selectedOption ->
                    activeChoice = activeChoice?.copy(
                        value = selectedOption.label,
                        choices = activeChoice?.choices.orEmpty().map { option ->
                            option.copy(selected = option.key == selectedOption.key)
                        },
                    )
                    selectedOption.onSelect()
                },
                onDismiss = { activeChoice = null },
            )
        }
    }
}

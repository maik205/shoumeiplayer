package com.maik205.shoumeiplayer.ui.television.navigation

import android.widget.Toast
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.data.session.SessionEvent
import com.maik205.shoumeiplayer.di.LocalAppContainer
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingShape
import com.maik205.shoumeiplayer.ui.television.components.TelevisionErrorState
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusRevealButton
import com.maik205.shoumeiplayer.domain.model.LibraryDestination as LibraryDestinationUi
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.ui.television.screens.browse.TelevisionHomeScreen
import com.maik205.shoumeiplayer.ui.television.screens.browse.TelevisionHomeViewModel
import com.maik205.shoumeiplayer.ui.television.screens.browse.TelevisionLibraryScreen
import com.maik205.shoumeiplayer.ui.television.screens.browse.TelevisionLibraryViewModel
import com.maik205.shoumeiplayer.ui.television.screens.detail.TelevisionDetailScreen
import com.maik205.shoumeiplayer.ui.television.screens.detail.TelevisionDetailViewModel
import com.maik205.shoumeiplayer.ui.television.screens.live.TelevisionLiveScreen
import com.maik205.shoumeiplayer.ui.television.screens.live.TelevisionLiveViewModel
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.ConnectEvent
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.ConnectScreen
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.ConnectViewModel
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.CompactStateScreen
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.LoginEvent
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.LoginScreen
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.ProfilesEvent
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.ProfilesScreen
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.ProfilesViewModel
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.RecoveryScreen
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.TelevisionLoginViewModel
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.TelevisionRecoveryViewModel
import com.maik205.shoumeiplayer.ui.television.screens.player.TelevisionPlayerScreen
import com.maik205.shoumeiplayer.ui.television.screens.screensaver.ScreensaverHost
import com.maik205.shoumeiplayer.ui.television.screens.search.TelevisionSearchViewModel
import com.maik205.shoumeiplayer.ui.television.screens.settings.TelevisionSettingsEvent
import com.maik205.shoumeiplayer.ui.television.screens.settings.TelevisionSettingsScreen
import com.maik205.shoumeiplayer.ui.television.screens.settings.TelevisionSettingsViewModel
import com.maik205.shoumeiplayer.ui.television.screens.browse.TelevisionSearchScreen
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import com.maik205.shoumeiplayer.ui.i18n.resolve

private val TelevisionEnter: EnterTransition = fadeIn(tween(100))
private val TelevisionExit: ExitTransition = fadeOut(tween(80))

@Composable
fun TelevisionNavGraph(
    onReady: () -> Unit = {},
) {
    val container = LocalAppContainer.current
    val rootViewModel = containerViewModel { container ->
        TelevisionRootViewModel(container.sessionStore)
    }
    val root by rootViewModel.start.collectAsStateWithLifecycle()

    LaunchedEffect(root) {
        if (root != TelevisionStart.Loading) onReady()
    }

    if (root == TelevisionStart.Loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TelevisionTheme.colors.Black),
        ) {
            TelevisionLoadingState(
                label = stringResource(R.string.tv_opening_shoumei),
                shape = TelevisionLoadingShape.Startup,
                modifier = Modifier.padding(start = 54.dp, top = 210.dp),
            )
        }
        return
    }

    if (root == TelevisionStart.Error) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TelevisionTheme.colors.Black),
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 54.dp),
                horizontalAlignment = Alignment.Start,
            ) {
                TelevisionErrorState(
                    title = stringResource(R.string.tv_startup_error),
                    message = stringResource(R.string.tv_startup_error_detail),
                    onRetry = rootViewModel::retry,
                    retryLabel = stringResource(R.string.retry),
                )
                Spacer(Modifier.height(18.dp))
                TelevisionFocusRevealButton(
                    label = stringResource(R.string.connect),
                    icon = Icons.Default.CastConnected,
                    onClick = rootViewModel::continueToConnect,
                    expandedWidth = 130.dp,
                )
            }
        }
        return
    }

    val navController = rememberNavController()
    val shellViewModel = containerViewModel { container ->
        TelevisionShellViewModel(
            sessionStore = container.sessionStore,
            mediaCatalog = container.mediaCatalog,
            libraryCacheStore = container.libraryCacheStore,
            settingsStore = container.settingsStore,
            authRepository = container.authRepository,
            imageUrlBuilder = container.imageUrlBuilder,
        )
    }
    val shell by shellViewModel.state.collectAsStateWithLifecycle()
    val topNavigationState = rememberLazyListState()
    val startRoute: Any = when (root) {
        TelevisionStart.Connect -> ConnectRoute
        TelevisionStart.Profiles -> ProfilesRoute
        TelevisionStart.Home -> HomeRoute
        TelevisionStart.Loading -> ConnectRoute
        TelevisionStart.Error -> ConnectRoute
    }

    LaunchedEffect(navController) {
        container.sessionManager.events.collect { event ->
            if (event == SessionEvent.Expired) {
                navController.navigate(SessionExpiredRoute) {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
    }

    // The screensaver wraps the whole navigation graph so idle detection and dismissal are
    // one concern owned in one place, rather than something every destination has to remember.
    ScreensaverHost {
        NavHost(
            navController = navController,
            startDestination = startRoute,
            enterTransition = { TelevisionEnter },
            exitTransition = { TelevisionExit },
            popEnterTransition = { TelevisionEnter },
            popExitTransition = { TelevisionExit },
        ) {
            composable<ConnectRoute> {
                val viewModel = containerViewModel { container ->
                    ConnectViewModel(container.authRepository, container.discoveryRepository)
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        if (event is ConnectEvent.Connected) {
                            if (event.resumeSession) {
                                navController.navigate(HomeRoute) {
                                    popUpTo(ConnectRoute) { inclusive = true }
                                }
                            } else {
                                navController.navigate(ProfilesRoute) {
                                    popUpTo(ConnectRoute) { inclusive = true }
                                }
                            }
                        }
                    }
                }
                ConnectScreen(
                    state = state,
                    onAddressChange = viewModel::setAddress,
                    onRefresh = viewModel::refresh,
                    onServerClick = viewModel::connectSelected,
                    onConnect = viewModel::connectManual,
                    onRetryConnection = viewModel::retryConnection,
                    onAcceptInsecureConnection = viewModel::acceptInsecureConnection,
                    onCancelInsecureConnection = viewModel::cancelInsecureConnection,
                )
            }

            composable<ProfilesRoute> {
                val viewModel = containerViewModel { container ->
                    ProfilesViewModel(container.authRepository, container.imageUrlBuilder)
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        when (event) {
                            ProfilesEvent.Home -> navController.navigate(HomeRoute) {
                                popUpTo(0) { inclusive = true }
                            }
                            is ProfilesEvent.Login -> navController.navigate(LoginRoute(event.userName)) {
                                launchSingleTop = true
                            }
                        }
                    }
                }
                ProfilesScreen(
                    state = state,
                    backdropUrl = container.imageUrlBuilder.serverSplashscreen(),
                    onProfileClick = viewModel::choose,
                    onRetry = viewModel::retry,
                    onAnotherAccount = viewModel::useAnotherAccount,
                    // Profiles is reached two ways: as the onboarding start destination, where
                    // Back belongs to the server picker, and as a push from the navbar avatar of
                    // an authenticated screen, where Back belongs to whatever opened it. Clearing
                    // the stack unconditionally threw away a signed-in session for anyone who
                    // opened the avatar to look and then backed out.
                    onBack = { navController.backOrReplaceWith(ConnectRoute) },
                )
            }

            composable<LoginRoute> { entry ->
                val route = entry.toRoute<LoginRoute>()
                val viewModel = containerViewModel { container ->
                    TelevisionLoginViewModel(container.authRepository, route.userName)
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        when (event) {
                            LoginEvent.Home -> navController.navigate(HomeRoute) {
                                popUpTo(0) { inclusive = true }
                            }
                            LoginEvent.AccountLocked -> navController.navigate(AccountLockedRoute) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    }
                }
                LoginScreen(
                    state = state,
                    backdropUrl = container.imageUrlBuilder.serverSplashscreen(),
                    onUserNameChange = viewModel::setUserName,
                    onPasswordChange = viewModel::setPassword,
                    onSignIn = viewModel::signIn,
                    onQuickConnect = viewModel::generateQuickConnect,
                    onForgotPassword = {
                        navController.pushSingleTop(RecoveryRoute(state.userName))
                    },
                    onBack = { navController.backOrReplaceWith(ProfilesRoute) },
                )
            }

            composable<RecoveryRoute> { entry ->
                val route = entry.toRoute<RecoveryRoute>()
                val viewModel = containerViewModel { container ->
                    TelevisionRecoveryViewModel(container.authRepository, route.userName)
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                RecoveryScreen(
                    state = state,
                    backdropUrl = container.imageUrlBuilder.serverSplashscreen(),
                    onUserNameChange = viewModel::setUserName,
                    onRequestReset = viewModel::requestReset,
                    onBack = navController::popBackStack,
                )
            }

            composable<SessionExpiredRoute> {
                CompactStateScreen(
                    title = stringResource(R.string.tv_session_expired_title),
                    backdropUrl = container.imageUrlBuilder.serverSplashscreen(),
                    detail = stringResource(R.string.tv_session_expired_detail),
                    primaryLabel = stringResource(R.string.tv_sign_in_again),
                    onPrimary = {
                        navController.navigate(LoginRoute()) {
                            popUpTo(SessionExpiredRoute) { inclusive = true }
                        }
                    },
                    secondaryLabel = stringResource(R.string.tv_profiles),
                    onSecondary = {
                        navController.navigate(ProfilesRoute) {
                            popUpTo(SessionExpiredRoute) { inclusive = true }
                        }
                    },
                )
            }

            composable<AccountLockedRoute> {
                CompactStateScreen(
                    title = stringResource(R.string.tv_account_locked_title),
                    backdropUrl = container.imageUrlBuilder.serverSplashscreen(),
                    detail = stringResource(R.string.tv_account_locked_detail),
                    primaryLabel = stringResource(R.string.tv_profiles),
                    onPrimary = {
                        navController.navigate(ProfilesRoute) {
                            popUpTo(AccountLockedRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable<HomeRoute> {
                val viewModel = containerViewModel { container ->
                    TelevisionHomeViewModel(
                        catalog = container.mediaCatalog,
                        sessionStore = container.sessionStore,
                        settingsStore = container.settingsStore,
                        libraryCacheStore = container.libraryCacheStore,
                        preferenceStore = container.preferenceStore,
                    )
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                // "Remember last library" (#88): once per Home visit, if the toggle is on and the
                // remembered library still exists, send the viewer straight there. Keyed on the
                // restoreLibrary value itself, so this fires exactly once -- consuming it clears the
                // state field, and an unchanged (already-null) key never re-triggers the effect.
                LaunchedEffect(state.restoreLibrary) {
                    state.restoreLibrary?.let { library ->
                        viewModel.consumeRestoreLibrary()
                        navController.navigateLibrary(library)
                    }
                }
                TelevisionHomeScreen(
                    state = state,
                    userName = shell.userName,
                    avatarUrl = shell.avatarUrl,
                    onRefresh = {
                        viewModel.refresh()
                        shellViewModel.refreshLibraries()
                    },
                    onItemFocused = viewModel::focus,
                    onOpenItem = { navController.pushSingleTop(DetailRoute(it.id)) },
                    onPlay = { media -> navController.pushSingleTop(media.toPlayerRoute()) },
                    onToggleFavorite = viewModel::toggleFavorite,
                    onNavigateHome = {},
                    onNavigateSearch = { navController.navigateTop(SearchRoute) },
                    onNavigateLibrary = { navController.navigateLibrary(it) },
                    onNavigateSettings = { navController.navigateTop(SettingsRoute) },
                    onNavigateProfile = { navController.pushSingleTop(ProfilesRoute) },
                    navigationState = topNavigationState,
                )
            }

            composable<SearchRoute> {
                val viewModel = containerViewModel { container ->
                    TelevisionSearchViewModel(container.mediaCatalog)
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                TelevisionSearchScreen(
                    query = state.query,
                    searching = state.searching,
                    results = state.results,
                    error = state.error,
                    resultLimitReached = state.resultLimitReached,
                    onQueryChange = viewModel::setQuery,
                    onRetry = viewModel::retry,
                    onOpenItem = { navController.pushSingleTop(DetailRoute(it.id)) },
                    onBack = navController::popBackStack,
                    libraries = shell.libraries,
                    userName = shell.userName,
                    avatarUrl = shell.avatarUrl,
                    onNavigateHome = { navController.navigateTop(HomeRoute) },
                    onNavigateLibrary = { navController.navigateLibrary(it) },
                    onNavigateSettings = { navController.navigateTop(SettingsRoute) },
                    onNavigateProfile = { navController.pushSingleTop(ProfilesRoute) },
                    navigationState = topNavigationState,
                )
            }

            composable<LibraryRoute> { entry ->
                val route = entry.toRoute<LibraryRoute>()
                val viewModel = containerViewModel { container ->
                    TelevisionLibraryViewModel(
                        catalog = container.mediaCatalog,
                        sessionStore = container.sessionStore,
                        settingsStore = container.settingsStore,
                        libraryCacheStore = container.libraryCacheStore,
                        preferenceStore = container.preferenceStore,
                        libraryId = route.libraryId,
                        title = route.title,
                        collectionType = route.collectionType,
                    )
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                TelevisionLibraryScreen(
                    state = state,
                    libraryId = route.libraryId,
                    libraries = shell.libraries,
                    userName = shell.userName,
                    avatarUrl = shell.avatarUrl,
                    onRetry = viewModel::reload,
                    onLoadMore = viewModel::loadMore,
                    onSetSort = viewModel::setSort,
                    onSetView = viewModel::setView,
                    onOpenItem = { media ->
                        if (media.type == "Audio") {
                            navController.pushSingleTop(media.toPlayerRoute())
                        } else {
                            navController.pushSingleTop(DetailRoute(media.id))
                        }
                    },
                    onNavigateHome = { navController.navigateTop(HomeRoute) },
                    onNavigateSearch = { navController.navigateTop(SearchRoute) },
                    onNavigateLibrary = { navController.navigateLibrary(it) },
                    onNavigateSettings = { navController.navigateTop(SettingsRoute) },
                    onNavigateProfile = { navController.pushSingleTop(ProfilesRoute) },
                    navigationState = topNavigationState,
                )
            }

            composable<DetailRoute> { entry ->
                val route = entry.toRoute<DetailRoute>()
                DetailDestination(
                    itemId = route.itemId,
                    navController = navController,
                )
            }

            composable<PersonRoute> { entry ->
                val route = entry.toRoute<PersonRoute>()
                DetailDestination(
                    itemId = route.personId,
                    navController = navController,
                )
            }

            composable<LiveTvRoute> {
                val viewModel = containerViewModel { container ->
                    TelevisionLiveViewModel(container.libraryRepository, container.imageUrlBuilder)
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                TelevisionLiveScreen(
                    state = state,
                    onBack = navController::popBackStack,
                    onRefresh = viewModel::refresh,
                    onFocusProgram = viewModel::focus,
                    onOpenProgram = { navController.pushSingleTop(DetailRoute(it.id)) },
                    onPlay = { navController.pushSingleTop(it.toPlayerRoute()) },
                    libraries = shell.libraries,
                    userName = shell.userName,
                    avatarUrl = shell.avatarUrl,
                    onNavigateHome = { navController.navigateTop(HomeRoute) },
                    onNavigateSearch = { navController.navigateTop(SearchRoute) },
                    onNavigateLibrary = { navController.navigateLibrary(it) },
                    onNavigateSettings = { navController.navigateTop(SettingsRoute) },
                    onNavigateProfile = { navController.pushSingleTop(ProfilesRoute) },
                    navigationState = topNavigationState,
                )
            }

            composable<SettingsRoute> {
                val viewModel = containerViewModel { container ->
                    TelevisionSettingsViewModel(
                        settingsStore = container.settingsStore,
                        sessionStore = container.sessionStore,
                        authRepository = container.authRepository,
                        artworkCache = container.artworkCache,
                        capabilities = container.devicePlaybackCapabilities,
                    )
                }
                val state by viewModel.state.collectAsStateWithLifecycle()
                val context = LocalContext.current
                LaunchedEffect(viewModel) {
                    viewModel.events.collect { event ->
                        when (event) {
                            TelevisionSettingsEvent.Profiles -> navController.navigate(ProfilesRoute) {
                                popUpTo(0) { inclusive = true }
                            }
                            TelevisionSettingsEvent.Connect -> navController.navigate(ConnectRoute) {
                                popUpTo(0) { inclusive = true }
                            }
                            is TelevisionSettingsEvent.CacheMessage -> {
                                Toast.makeText(context, event.message.resolve(context), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                TelevisionSettingsScreen(
                    state = state,
                    libraries = shell.libraries,
                    avatarUrl = shell.avatarUrl,
                    onUpdate = viewModel::update,
                    onSignOut = viewModel::signOut,
                    onChangeServer = viewModel::changeServer,
                    onForgetServer = viewModel::forgetServer,
                    onSwitchProfile = viewModel::switchProfile,
                    onTestConnection = viewModel::testConnection,
                    onRefreshLibraries = shellViewModel::refreshLibraries,
                    libraryRefreshing = shell.loadingLibraries,
                    libraryRefreshError = shell.error,
                    onRetrySettings = viewModel::retryLastUpdate,
                    onQuickConnect = viewModel::generateQuickConnect,
                    onClearArtworkCache = viewModel::clearArtworkCache,
                    onNavigateHome = { navController.navigateTop(HomeRoute) },
                    onNavigateSearch = { navController.navigateTop(SearchRoute) },
                    onNavigateLibrary = { navController.navigateLibrary(it) },
                    onNavigateProfile = { navController.pushSingleTop(ProfilesRoute) },
                    navigationState = topNavigationState,
                )
            }

            composable<PlayerRoute> { entry ->
                val route = entry.toRoute<PlayerRoute>()
                TelevisionPlayerScreen(
                    itemId = route.itemId,
                    startPositionTicks = route.startPositionTicks,
                    audioOnly = route.audioOnly,
                    initialAudioStreamIndex = route.initialAudioStreamIndex,
                    initialSubtitleStreamIndex = route.initialSubtitleStreamIndex,
                    initialQualityLabel = route.initialQualityLabel,
                    onExit = navController::popBackStack,
                    // Leaving the player for another item is one navigation, not a pop followed
                    // by a push: doing it in two steps popped whatever happened to be on top and
                    // stacked a second copy when the viewer pressed twice. Replacing the player
                    // entry in a single call is atomic and idempotent.
                    onNavigateToItem = { itemId ->
                        navController.navigate(DetailRoute(itemId)) {
                            popUpTo<PlayerRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToPerson = { personId, name ->
                        navController.navigate(PersonRoute(personId, name)) {
                            popUpTo<PlayerRoute> { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun DetailDestination(
    itemId: String,
    navController: NavHostController,
) {
    val viewModel = containerViewModel { container ->
        TelevisionDetailViewModel(
            repository = container.mediaDetailsRepository,
            itemId = itemId,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TelevisionDetailScreen(
        state = state,
        onBack = navController::popBackStack,
        onRetry = viewModel::reload,
        onRetryAction = viewModel::retryLastAction,
        onPlay = { targetId, ticks, audioOnly, audioIndex, subtitleIndex, quality ->
            navController.pushSingleTop(
                PlayerRoute(
                    itemId = targetId,
                    startPositionTicks = ticks,
                    audioOnly = audioOnly,
                    initialAudioStreamIndex = audioIndex,
                    initialSubtitleStreamIndex = subtitleIndex,
                    initialQualityLabel = quality,
                ),
            )
        },
        onToggleFavorite = viewModel::toggleFavorite,
        onTogglePlayed = viewModel::togglePlayed,
        onSelectSeason = viewModel::selectSeason,
        onOpenItem = { navController.pushSingleTop(DetailRoute(it.id)) },
        onOpenPerson = { navController.pushSingleTop(PersonRoute(it.id, it.name)) },
    )
}

/**
 * Returns to whatever opened this destination, falling back to [replacement] as a fresh root when
 * nothing is underneath. Screens that can be either a pushed child or the start destination need
 * both behaviours, and `popBackStack()`'s return value does not distinguish them reliably enough
 * to branch on.
 */
private fun NavHostController.backOrReplaceWith(replacement: Any) {
    if (previousBackStackEntry != null) {
        popBackStack()
    } else {
        navigate(replacement) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    }
}

/**
 * Pushes [route] at most once per press. A remote's key repeat, or an impatient second press on a
 * card while the transition runs, would otherwise stack duplicate copies of the same destination
 * and make the viewer press Back once per stray press to escape.
 */
private fun NavHostController.pushSingleTop(route: Any) {
    navigate(route) { launchSingleTop = true }
}

private fun NavHostController.navigateLibrary(library: LibraryDestinationUi) {
    if (library.collectionType.equals("livetv", ignoreCase = true)) {
        navigateTop(LiveTvRoute)
    } else {
        navigateTop(
            LibraryRoute(
                libraryId = library.id,
                title = library.title,
                collectionType = library.collectionType,
            ),
            // Library tabs are different argument instances of the same LibraryRoute destination.
            // Restoring by destination ID can therefore revive the library that was just popped
            // and discard the newly selected library's arguments, making consecutive nav clicks
            // look blocked. Top-nav selection is authoritative, so always create the route.
            restoreDestinationState = false,
        )
    }
}

/**
 * Switches the top-navigation destination, preserving what the viewer left behind.
 *
 * Saving state on the way out and restoring it on the way in is what keeps a Search query and its
 * results, a library's scroll position, and the selected Settings section alive across a tab
 * switch -- the back-stack entry, and with it the destination's ViewModel, is stashed rather than
 * destroyed. Libraries opt out of the restore half for the reason given at the call site.
 */
private fun NavHostController.navigateTop(route: Any, restoreDestinationState: Boolean = true) {
    navigate(route) {
        launchSingleTop = true
        restoreState = restoreDestinationState
        popUpTo(HomeRoute) { saveState = true }
    }
}

private fun MediaItemUi.toPlayerRoute(): PlayerRoute = PlayerRoute(
    itemId = id,
    startPositionTicks = resumeTicks,
    audioOnly = type in setOf("Audio", "AudioBook"),
)

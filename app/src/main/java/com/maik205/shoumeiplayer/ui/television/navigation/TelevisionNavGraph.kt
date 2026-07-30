package com.maik205.shoumeiplayer.ui.television.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.di.AuthEvents
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.television.components.TelevisionLoadingState
import com.maik205.shoumeiplayer.ui.television.model.LibraryDestinationUi
import com.maik205.shoumeiplayer.ui.television.model.MediaItemUi
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
import com.maik205.shoumeiplayer.ui.television.screens.search.TelevisionSearchViewModel
import com.maik205.shoumeiplayer.ui.television.screens.settings.TelevisionSettingsEvent
import com.maik205.shoumeiplayer.ui.television.screens.settings.TelevisionSettingsScreen
import com.maik205.shoumeiplayer.ui.television.screens.settings.TelevisionSettingsViewModel
import com.maik205.shoumeiplayer.ui.television.screens.browse.TelevisionSearchScreen
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors

private val TelevisionEnter: EnterTransition = fadeIn(tween(100))
private val TelevisionExit: ExitTransition = fadeOut(tween(80))

@Composable
fun TelevisionNavGraph() {
    val rootViewModel = containerViewModel { container ->
        TelevisionRootViewModel(container.sessionStore)
    }
    val root by rootViewModel.start.collectAsStateWithLifecycle()

    if (root == TelevisionStart.Loading) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TelevisionColors.Black),
        ) {
            TelevisionLoadingState(
                label = stringResource(R.string.tv_opening_shoumei),
                modifier = Modifier.padding(start = 54.dp, top = 210.dp),
            )
        }
        return
    }

    val navController = rememberNavController()
    val shellViewModel = containerViewModel { container ->
        TelevisionShellViewModel(
            sessionStore = container.sessionStore,
            libraryRepository = container.libraryRepository,
            authRepository = container.authRepository,
            imageUrlBuilder = container.imageUrlBuilder,
        )
    }
    val shell by shellViewModel.state.collectAsStateWithLifecycle()
    val startRoute: Any = when (root) {
        TelevisionStart.Connect -> ConnectRoute
        TelevisionStart.Profiles -> ProfilesRoute
        TelevisionStart.Home -> HomeRoute
        TelevisionStart.Loading -> ConnectRoute
    }

    LaunchedEffect(navController) {
        AuthEvents.unauthorized.collect {
            navController.navigate(SessionExpiredRoute) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

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
                    if (event == ConnectEvent.Connected) {
                        navController.navigate(ProfilesRoute) {
                            popUpTo(ConnectRoute) { inclusive = true }
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
                        is ProfilesEvent.Login -> navController.navigate(LoginRoute(event.userName))
                    }
                }
            }
            ProfilesScreen(
                state = state,
                onProfileClick = viewModel::choose,
                onAnotherAccount = viewModel::useAnotherAccount,
                onBack = {
                    navController.navigate(ConnectRoute) {
                        launchSingleTop = true
                    }
                },
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
                            popUpTo(ProfilesRoute) { inclusive = false }
                        }
                    }
                }
            }
            LoginScreen(
                state = state,
                onUserNameChange = viewModel::setUserName,
                onPasswordChange = viewModel::setPassword,
                onSignIn = viewModel::signIn,
                onQuickConnect = viewModel::generateQuickConnect,
                onForgotPassword = {
                    navController.navigate(RecoveryRoute(state.userName))
                },
                onBack = navController::popBackStack,
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
                onUserNameChange = viewModel::setUserName,
                onRequestReset = viewModel::requestReset,
                onBack = navController::popBackStack,
            )
        }

        composable<SessionExpiredRoute> {
            CompactStateScreen(
                title = stringResource(R.string.tv_session_expired_title),
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
                detail = stringResource(R.string.tv_account_locked_detail),
                primaryLabel = stringResource(R.string.tv_profiles),
                onPrimary = {
                    navController.navigate(ProfilesRoute) {
                        popUpTo(AccountLockedRoute) { inclusive = true }
                    }
                },
            )
        }

        composable<HomeRoute> {
            val viewModel = containerViewModel { container ->
                TelevisionHomeViewModel(container.libraryRepository, container.imageUrlBuilder)
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            TelevisionHomeScreen(
                state = state,
                userName = shell.userName,
                avatarUrl = shell.avatarUrl,
                onRefresh = {
                    viewModel.refresh()
                    shellViewModel.refreshLibraries()
                },
                onItemFocused = viewModel::focus,
                onOpenItem = { navController.navigate(DetailRoute(it.id)) },
                onPlay = { media -> navController.navigate(media.toPlayerRoute()) },
                onToggleFavorite = viewModel::toggleFavorite,
                onNavigateHome = {},
                onNavigateSearch = { navController.navigateTop(SearchRoute) },
                onNavigateLibrary = { navController.navigateLibrary(it) },
                onNavigateSettings = { navController.navigateTop(SettingsRoute) },
                onNavigateProfile = { navController.navigate(ProfilesRoute) },
            )
        }

        composable<SearchRoute> {
            val viewModel = containerViewModel { container ->
                TelevisionSearchViewModel(container.libraryRepository, container.imageUrlBuilder)
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            TelevisionSearchScreen(
                query = state.query,
                searching = state.searching,
                results = state.results,
                error = state.error,
                onQueryChange = viewModel::setQuery,
                onRetry = viewModel::retry,
                onOpenItem = { navController.navigate(DetailRoute(it.id)) },
                onBack = navController::popBackStack,
            )
        }

        composable<LibraryRoute> { entry ->
            val route = entry.toRoute<LibraryRoute>()
            val viewModel = containerViewModel { container ->
                TelevisionLibraryViewModel(
                    repository = container.libraryRepository,
                    images = container.imageUrlBuilder,
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
                        navController.navigate(media.toPlayerRoute())
                    } else {
                        navController.navigate(DetailRoute(media.id))
                    }
                },
                onNavigateHome = { navController.navigateTop(HomeRoute) },
                onNavigateSearch = { navController.navigateTop(SearchRoute) },
                onNavigateLibrary = { navController.navigateLibrary(it) },
                onNavigateSettings = { navController.navigateTop(SettingsRoute) },
                onNavigateProfile = { navController.navigate(ProfilesRoute) },
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
                onOpenProgram = { navController.navigate(DetailRoute(it.id)) },
                onPlay = { navController.navigate(it.toPlayerRoute()) },
            )
        }

        composable<SettingsRoute> {
            val viewModel = containerViewModel { container ->
                TelevisionSettingsViewModel(
                    settingsStore = container.settingsStore,
                    sessionStore = container.sessionStore,
                    authRepository = container.authRepository,
                )
            }
            val state by viewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(viewModel) {
                viewModel.events.collect { event ->
                    when (event) {
                        TelevisionSettingsEvent.Profiles -> navController.navigate(ProfilesRoute) {
                            popUpTo(0) { inclusive = true }
                        }
                        TelevisionSettingsEvent.Connect -> navController.navigate(ConnectRoute) {
                            popUpTo(0) { inclusive = true }
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
                onSwitchProfile = viewModel::switchProfile,
                onTestConnection = viewModel::testConnection,
                onRefreshLibraries = shellViewModel::refreshLibraries,
                onQuickConnect = viewModel::generateQuickConnect,
                onNavigateHome = { navController.navigateTop(HomeRoute) },
                onNavigateSearch = { navController.navigateTop(SearchRoute) },
                onNavigateLibrary = { navController.navigateLibrary(it) },
                onNavigateProfile = { navController.navigate(ProfilesRoute) },
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
                onNavigateToItem = { itemId ->
                    navController.popBackStack()
                    navController.navigate(DetailRoute(itemId))
                },
                onNavigateToPerson = { personId, name ->
                    navController.popBackStack()
                    navController.navigate(PersonRoute(personId, name))
                },
            )
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
            repository = container.libraryRepository,
            images = container.imageUrlBuilder,
            itemId = itemId,
        )
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    TelevisionDetailScreen(
        state = state,
        onBack = navController::popBackStack,
        onRetry = viewModel::reload,
        onPlay = { targetId, ticks, audioOnly, audioIndex, subtitleIndex, quality ->
            navController.navigate(
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
        onOpenItem = { navController.navigate(DetailRoute(it.id)) },
        onOpenPerson = { navController.navigate(PersonRoute(it.id, it.name)) },
    )
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
        )
    }
}

private fun NavHostController.navigateTop(route: Any) {
    navigate(route) {
        launchSingleTop = true
        // Library tabs are different argument instances of the same LibraryRoute destination.
        // Restoring by destination ID can therefore revive the library that was just popped and
        // discard the newly selected library's arguments, making consecutive nav clicks look
        // blocked. Top-nav selection is authoritative, so always create the requested route.
        popUpTo(HomeRoute)
    }
}

private fun MediaItemUi.toPlayerRoute(): PlayerRoute = PlayerRoute(
    itemId = id,
    startPositionTicks = resumeTicks,
    audioOnly = type in setOf("Audio", "AudioBook"),
)

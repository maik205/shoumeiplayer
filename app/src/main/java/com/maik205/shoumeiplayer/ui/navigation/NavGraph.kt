package com.maik205.shoumeiplayer.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.maik205.shoumeiplayer.di.AppContainer
import com.maik205.shoumeiplayer.di.AuthEvents
import com.maik205.shoumeiplayer.di.LocalAppContainer
import com.maik205.shoumeiplayer.ui.RootViewModel
import com.maik205.shoumeiplayer.ui.StartDestination
import com.maik205.shoumeiplayer.ui.components.LoadingView
import com.maik205.shoumeiplayer.ui.components.NavRailDestination
import com.maik205.shoumeiplayer.ui.screens.detail.DetailScreen
import com.maik205.shoumeiplayer.ui.screens.home.HomeScreen
import com.maik205.shoumeiplayer.ui.screens.libraries.LibrariesScreen
import com.maik205.shoumeiplayer.ui.screens.library.LibraryScreen
import com.maik205.shoumeiplayer.ui.screens.login.LoginScreen
import com.maik205.shoumeiplayer.ui.screens.player.PlayerScreen
import com.maik205.shoumeiplayer.ui.screens.search.SearchScreen
import com.maik205.shoumeiplayer.ui.screens.serverentry.ServerEntryScreen
import com.maik205.shoumeiplayer.ui.screens.settings.SettingsScreen
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Ease
import com.maik205.shoumeiplayer.ui.theme.Ink000

/**
 * Resolves a ViewModel via the [AppContainer] reached through [LocalAppContainer],
 * bypassing the default no-arg ViewModel factory. Screens/other NavGraph editors
 * (M3.3–M3.5, M4.4, M5.4, M6.1) should use this instead of hand-rolling factories.
 */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    noinline factory: (AppContainer) -> VM,
): VM {
    val container = LocalAppContainer.current
    return viewModel(
        factory = viewModelFactory {
            initializer { factory(container) }
        },
    )
}

/**
 * §6 / §7 D1 — every route arrives on `fadeIn(220)` and leaves on `fadeOut(180)`. **No horizontal
 * slides** (a phone idiom that reads cheap at 55"), and exits never animate position — they fight
 * Back. Detail alone adds `scaleIn(0.98f)` on top of the fade.
 */
private val ScreenEnter: EnterTransition = fadeIn(tween(Dur.ScreenIn))
private val ScreenExit: ExitTransition = fadeOut(tween(Dur.ScreenOut))
private val DetailEnter: EnterTransition = ScreenEnter +
    scaleIn(initialScale = 0.98f, animationSpec = tween(Dur.ScreenIn, easing = Ease.Decel))

/** §5.6 — every state view parks at x=48, y=220; nothing is centred outside a dialog (§1). */
private val StateTop = 220.dp

/**
 * M-B14 / §3.1 — the one rail hop, shared by Home, Libraries, Search and Settings.
 *
 * `launchSingleTop` plus `popUpTo(HomeRoute)` means a rail move never stacks: Home stays the single
 * root of the authed graph, whatever is above it is popped, and BACK from any rail destination
 * lands on Home rather than walking a chain of previous rail visits. `saveState`/`restoreState`
 * keep each destination's scroll and focus position across the hop.
 */
private fun NavHostController.navigateToRail(destination: NavRailDestination) {
    val route: Any = when (destination) {
        NavRailDestination.Home -> HomeRoute
        NavRailDestination.Search -> SearchRoute
        NavRailDestination.Libraries -> LibrariesRoute
        NavRailDestination.Settings -> SettingsRoute
    }
    navigate(route) {
        launchSingleTop = true
        popUpTo(HomeRoute) { saveState = true }
        restoreState = true
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun NavGraph() {
    val rootViewModel = containerViewModel { container -> RootViewModel(container.sessionStore) }
    val start by rootViewModel.start.collectAsStateWithLifecycle()

    when (val destination = start) {
        StartDestination.Loading -> {
            // §1 anti-goal — no centred spinner: the session probe is one left-aligned word on
            // black, at the same x=48 / y=220 anchor every other state view uses (§5.6).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink000),
            ) {
                LoadingView(
                    modifier = Modifier.padding(
                        start = Dimens.OverscanHorizontal,
                        top = StateTop,
                    ),
                )
            }
        }

        StartDestination.ServerEntry, StartDestination.Login, StartDestination.Home -> {
            val navController = rememberNavController()
            val startRoute: Any = when (destination) {
                StartDestination.ServerEntry -> ServerEntryRoute
                StartDestination.Login -> LoginRoute
                StartDestination.Home -> HomeRoute
                StartDestination.Loading -> ServerEntryRoute // unreachable in this branch
            }

            // M6.2 — any 401 clears the session in JellyfinClient and bounces here.
            // popUpTo(0) { inclusive = true } makes the authed graph unreachable via Back.
            LaunchedEffect(navController) {
                AuthEvents.unauthorized.collect {
                    navController.navigate(LoginRoute) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            }

            NavHost(
                navController = navController,
                startDestination = startRoute,
                enterTransition = { ScreenEnter },
                exitTransition = { ScreenExit },
                popEnterTransition = { ScreenEnter },
                popExitTransition = { ScreenExit },
            ) {
                composable<ServerEntryRoute> {
                    ServerEntryScreen(
                        onConnected = {
                            navController.navigate(LoginRoute)
                        },
                    )
                }
                composable<LoginRoute> {
                    LoginScreen(
                        onLoggedIn = {
                            navController.navigate(HomeRoute) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }
                composable<HomeRoute> {
                    HomeScreen(
                        onNavigateToDetail = { itemId ->
                            navController.navigate(DetailRoute(itemId))
                        },
                        onNavigateToLibrary = { libraryId, title, collectionType ->
                            navController.navigate(LibraryRoute(libraryId, title, collectionType))
                        },
                        onNavigate = navController::navigateToRail,
                    )
                }
                composable<LibrariesRoute> {
                    LibrariesScreen(
                        onNavigateToLibrary = { libraryId, title, collectionType ->
                            navController.navigate(LibraryRoute(libraryId, title, collectionType))
                        },
                        onNavigate = navController::navigateToRail,
                    )
                }
                composable<LibraryRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<LibraryRoute>()
                    LibraryScreen(
                        route = route,
                        onNavigateToDetail = { itemId ->
                            navController.navigate(DetailRoute(itemId))
                        },
                        onBack = { navController.popBackStack() },
                        onNavigate = navController::navigateToRail,
                    )
                }
                composable<DetailRoute>(
                    // §6 — Detail is the one route that scales in; its backdrop and copy column
                    // carry the rest of the staggered entrance inside the screen itself.
                    enterTransition = { DetailEnter },
                    popEnterTransition = { DetailEnter },
                ) { backStackEntry ->
                    val route = backStackEntry.toRoute<DetailRoute>()
                    DetailScreen(
                        itemId = route.itemId,
                        onPlay = { itemId, startTicks ->
                            navController.navigate(PlayerRoute(itemId, startTicks))
                        },
                        onNavigateToDetail = { itemId ->
                            navController.navigate(DetailRoute(itemId))
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
                composable<SearchRoute> {
                    SearchScreen(
                        onNavigateToDetail = { itemId ->
                            navController.navigate(DetailRoute(itemId))
                        },
                        onBack = { navController.popBackStack() },
                        onNavigate = navController::navigateToRail,
                    )
                }
                composable<SettingsRoute> {
                    SettingsScreen(
                        // M6.1 — sign out clears the session, then Login replaces the whole back
                        // stack so Home is unreachable via Back.
                        onSignedOut = {
                            navController.navigate(LoginRoute) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                        onNavigate = navController::navigateToRail,
                    )
                }
                composable<PlayerRoute> { backStackEntry ->
                    val route = backStackEntry.toRoute<PlayerRoute>()
                    PlayerScreen(
                        itemId = route.itemId,
                        startPositionTicks = route.startPositionTicks,
                        onExit = { navController.popBackStack() },
                        // docs/osd-v3.md §5 — leaving the player from a shelf pops it first, so the
                        // destination lands on the back stack the player was launched from rather
                        // than on top of a screen that is already tearing its transcode down.
                        onNavigateToItem = { itemId ->
                            navController.popBackStack()
                            navController.navigate(DetailRoute(itemId))
                        },
                        onNavigateToPerson = { personId, name ->
                            navController.popBackStack()
                            navController.navigate(
                                LibraryRoute(libraryId = "", title = name, personId = personId),
                            )
                        },
                    )
                }
            }
        }
    }
}

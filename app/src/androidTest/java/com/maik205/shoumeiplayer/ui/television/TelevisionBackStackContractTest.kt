package com.maik205.shoumeiplayer.ui.television

import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.maik205.shoumeiplayer.ui.television.navigation.ConnectRoute
import com.maik205.shoumeiplayer.ui.television.navigation.DetailRoute
import com.maik205.shoumeiplayer.ui.television.navigation.HomeRoute
import com.maik205.shoumeiplayer.ui.television.navigation.ProfilesRoute
import com.maik205.shoumeiplayer.ui.television.navigation.SearchRoute
import com.maik205.shoumeiplayer.ui.television.navigation.SettingsRoute
import com.maik205.shoumeiplayer.ui.television.navigation.backOrReplaceWith
import com.maik205.shoumeiplayer.ui.television.navigation.navigateTop
import com.maik205.shoumeiplayer.ui.television.navigation.PlayerRoute
import com.maik205.shoumeiplayer.ui.television.navigation.pushSingleTop
import com.maik205.shoumeiplayer.ui.television.navigation.replacePlayerWith
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Back-stack contracts (TEST-002).
 *
 * These drive the real navigation helpers from `TelevisionNavGraph` against a real
 * `NavHostController`, rather than re-implementing the rules in the test. The graph here is
 * deliberately minimal: what is under test is the helpers' effect on the stack, not the screens.
 *
 * Instrumented, so an emulator is required; the CI job compiles these but cannot run them.
 */
class TelevisionBackStackContractTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var navController: NavHostController

    private fun startAt(start: Any) {
        compose.setContent {
            navController = rememberNavController()
            NavHost(navController = navController, startDestination = start) {
                composable<ConnectRoute> { Text("connect") }
                composable<ProfilesRoute> { Text("profiles") }
                composable<HomeRoute> { Text("home") }
                composable<SearchRoute> { Text("search") }
                composable<SettingsRoute> { Text("settings") }
                composable<DetailRoute> { Text("detail") }
                composable<PlayerRoute> { Text("player") }
            }
        }
        compose.waitForIdle()
    }

    private fun currentRoute(): String? =
        navController.currentBackStackEntry?.destination?.route?.substringAfterLast('.')

    private fun backStackSize(): Int =
        navController.currentBackStack.value.count { it.destination.route != null }

    /** NAV-001: opening Profiles from an authenticated screen and backing out must not sign out. */
    @Test
    fun profilesBackReturnsToItsOpener() {
        startAt(HomeRoute)
        compose.runOnUiThread { navController.pushSingleTop(ProfilesRoute) }
        compose.waitForIdle()
        assertTrue(currentRoute()?.contains("ProfilesRoute") == true)

        compose.runOnUiThread { navController.backOrReplaceWith(ConnectRoute) }
        compose.waitForIdle()
        assertTrue("Back from Profiles must return Home", currentRoute()?.contains("HomeRoute") == true)
    }

    /** The same call, when Profiles is the start destination, has to fall back to Connect. */
    @Test
    fun profilesBackFallsBackToConnectWhenItIsTheRoot() {
        startAt(ProfilesRoute)
        compose.runOnUiThread { navController.backOrReplaceWith(ConnectRoute) }
        compose.waitForIdle()
        assertTrue(currentRoute()?.contains("ConnectRoute") == true)
    }

    /** NAV-010: key repeat on a card must not stack duplicate destinations. */
    @Test
    fun repeatedPushesDoNotStackDuplicates() {
        startAt(HomeRoute)
        val before = backStackSize()
        compose.runOnUiThread {
            navController.pushSingleTop(DetailRoute("item-1"))
            navController.pushSingleTop(DetailRoute("item-1"))
            navController.pushSingleTop(DetailRoute("item-1"))
        }
        compose.waitForIdle()
        assertEquals("One push per destination, however many times it is pressed", before + 1, backStackSize())
    }

    /** A genuinely different item is still a new entry. */
    @Test
    fun pushingADifferentItemIsStillANewEntry() {
        startAt(HomeRoute)
        compose.runOnUiThread { navController.pushSingleTop(DetailRoute("item-1")) }
        compose.waitForIdle()
        val afterFirst = backStackSize()
        compose.runOnUiThread { navController.pushSingleTop(DetailRoute("item-2")) }
        compose.waitForIdle()
        assertEquals(afterFirst + 1, backStackSize())
    }

    /**
     * NAV-007: leaving the player for another item replaces the player entry rather than popping
     * whatever happens to be on top and pushing a second copy on a double press.
     */
    @Test
    fun leavingThePlayerForAnItemReplacesThePlayerEntry() {
        startAt(HomeRoute)
        compose.runOnUiThread { navController.pushSingleTop(DetailRoute("origin")) }
        compose.waitForIdle()
        compose.runOnUiThread { navController.pushSingleTop(PlayerRoute("origin")) }
        compose.waitForIdle()
        val withPlayer = backStackSize()

        compose.runOnUiThread { navController.replacePlayerWith(DetailRoute("extra")) }
        compose.waitForIdle()
        assertEquals("Detail replaces the player, it does not stack on it", withPlayer, backStackSize())
        assertTrue(currentRoute()?.contains("DetailRoute") == true)

        // And the player is genuinely gone: Back returns to the Detail it was launched from.
        compose.runOnUiThread { navController.popBackStack() }
        compose.waitForIdle()
        assertTrue(currentRoute()?.contains("DetailRoute") == true)
    }

    /** Pressing the same extras target twice must not stack it either. */
    @Test
    fun repeatedPlayerCrossNavigationDoesNotStack() {
        startAt(HomeRoute)
        compose.runOnUiThread { navController.pushSingleTop(PlayerRoute("origin")) }
        compose.waitForIdle()
        compose.runOnUiThread {
            navController.replacePlayerWith(DetailRoute("extra"))
            navController.replacePlayerWith(DetailRoute("extra"))
        }
        compose.waitForIdle()
        assertEquals("Home plus one Detail", 2, backStackSize())
    }

    /** NAV-002: a top-navigation switch pops back to Home rather than growing the stack. */
    @Test
    fun topNavigationSwitchesDoNotGrowTheStack() {
        startAt(HomeRoute)
        compose.runOnUiThread { navController.navigateTop(SearchRoute) }
        compose.waitForIdle()
        val afterSearch = backStackSize()

        compose.runOnUiThread { navController.navigateTop(SettingsRoute) }
        compose.waitForIdle()
        assertEquals("Settings replaces Search above Home", afterSearch, backStackSize())
        assertTrue(currentRoute()?.contains("SettingsRoute") == true)
    }

    /** Back from a top-navigation destination returns to Home, which is the popUpTo anchor. */
    @Test
    fun backFromATopDestinationReturnsHome() {
        startAt(HomeRoute)
        compose.runOnUiThread { navController.navigateTop(SearchRoute) }
        compose.waitForIdle()
        compose.runOnUiThread { navController.popBackStack() }
        compose.waitForIdle()
        assertTrue(currentRoute()?.contains("HomeRoute") == true)
    }
}

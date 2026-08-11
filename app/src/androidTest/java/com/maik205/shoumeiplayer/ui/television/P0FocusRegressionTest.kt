package com.maik205.shoumeiplayer.ui.television

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import com.maik205.shoumeiplayer.domain.model.LibraryDestination
import com.maik205.shoumeiplayer.ui.i18n.UiText
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.TelevisionNavigationItem
import com.maik205.shoumeiplayer.ui.television.components.TelevisionTopNavigation
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.RecoveryScreen
import com.maik205.shoumeiplayer.ui.television.screens.onboarding.RecoveryUiState
import com.maik205.shoumeiplayer.ui.television.screens.player.PlayerExitConfirmationOverlay
import com.maik205.shoumeiplayer.ui.television.theme.ShoumeiTelevisionTheme
import org.junit.Rule
import org.junit.Test

class P0FocusRegressionTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun recoverySuccessFocusesAnActionInsteadOfResultText() {
        compose.setContent {
            ShoumeiTelevisionTheme {
                RecoveryScreen(
                    state = RecoveryUiState(
                        userName = "viewer",
                        result = UiText.Dynamic("Reset instructions sent"),
                    ),
                    backdropUrl = null,
                    onUserNameChange = {},
                    onRequestReset = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("recovery_action").assertIsFocused()
        compose.onNodeWithTag("recovery_username").assertIsNotFocused()
    }

    @Test
    fun recoveryFailureFocusesRetryActionInsteadOfErrorText() {
        compose.setContent {
            ShoumeiTelevisionTheme {
                RecoveryScreen(
                    state = RecoveryUiState(
                        userName = "viewer",
                        error = UiText.Dynamic("Server unavailable"),
                    ),
                    backdropUrl = null,
                    onUserNameChange = {},
                    onRequestReset = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithTag("recovery_action").assertIsFocused()
        compose.onNodeWithTag("recovery_username").assertIsNotFocused()
    }

    @Test
    fun exitConfirmationHasOneVisibleFocusedAction() {
        compose.setContent {
            ShoumeiTelevisionTheme {
                PlayerExitConfirmationOverlay(onConfirm = {}, onCancel = {})
            }
        }

        compose.onNodeWithText("Exit player").assertIsFocused()
        compose.onNodeWithText("Cancel").assertIsNotFocused()

        compose.onNodeWithText("Exit player").performKeyInput {
            pressKey(Key.DirectionRight)
        }
        compose.onNodeWithText("Cancel").assertIsFocused()
    }

    @Test
    fun delayedSelectedLibraryGetsInitialFocusWithoutReclaimingItOnRefresh() {
        val libraries = mutableStateOf(emptyList<LibraryDestination>())
        val contentFocus = FocusRequester()
        compose.setContent {
            ShoumeiTelevisionTheme {
                Column {
                    TelevisionTopNavigation(
                        primaryDestinations = listOf(
                            TelevisionNavigationItem("search", "Search", Icons.Default.Search),
                        ),
                        libraryDestinations = libraries.value,
                        selectedKey = "library:movies",
                        onDestinationClick = {},
                        onSettingsClick = {},
                        onAvatarClick = {},
                        avatarLabel = "Profile",
                    )
                    TelevisionFocusSurface(
                        onClick = {},
                        focusRequester = contentFocus,
                        modifier = Modifier.testTag("library_content"),
                    ) { _ -> }
                }
            }
        }

        compose.runOnIdle {
            libraries.value = listOf(LibraryDestination("movies", "Movies", "movies"))
        }
        compose.onNodeWithText("Movies").assertIsFocused()

        compose.runOnIdle {
            check(contentFocus.requestFocus())
            libraries.value = listOf(LibraryDestination("movies", "Films", "movies"))
        }
        compose.onNodeWithTag("library_content").assertIsFocused()
    }
}

package com.maik205.shoumeiplayer.ui.television

import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.input.key.Key
import com.maik205.shoumeiplayer.ui.i18n.UiText
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
}

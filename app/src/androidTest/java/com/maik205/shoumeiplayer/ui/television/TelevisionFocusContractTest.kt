package com.maik205.shoumeiplayer.ui.television

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.test.requestFocus
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.NativeKeyEvent
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusHandoff
import com.maik205.shoumeiplayer.ui.television.components.TelevisionFocusSurface
import com.maik205.shoumeiplayer.ui.television.components.televisionHorizontalWrap
import com.maik205.shoumeiplayer.ui.television.theme.ShoumeiTelevisionTheme
import org.junit.Rule
import org.junit.Test

/**
 * D-pad edge traversal and async focus transfer (TEST-003, TEST-004).
 *
 * These exercise the shared primitives directly rather than a whole screen: `televisionHorizontalWrap`
 * is what every rail's edges go through, and `TelevisionFocusHandoff` is what every disappearing
 * Retry button goes through. A fault in either shows up on many screens at once, which is what
 * makes them worth pinning here.
 *
 * Instrumented, so an emulator is required; the CI job compiles these but cannot run them.
 */
class TelevisionFocusContractTest {

    @get:Rule
    val compose = createComposeRule()

    private fun keyDown(keyCode: Int) = KeyEvent(
        NativeKeyEvent(AndroidKeyEvent.ACTION_DOWN, keyCode),
    )

    /** NAV-005: Right at the last tile wraps to the first rather than escaping the rail. */
    @Test
    fun railWrapsAtItsTrailingEdge() {
        val labels = listOf("a", "b", "c")
        compose.setContent {
            ShoumeiTelevisionTheme {
                val requesters = remember { labels.map { FocusRequester() } }
                val listState = rememberLazyListState()
                LazyRow(state = listState) {
                    items(labels.size) { index ->
                        TelevisionFocusSurface(
                            onClick = {},
                            focusRequester = requesters[index],
                            modifier = Modifier
                                .testTag(labels[index])
                                .televisionHorizontalWrap(index, requesters, listState),
                        ) { Text(labels[index]) }
                    }
                }
            }
        }

        compose.onNodeWithTag("c").requestFocus()
        compose.onNodeWithTag("c").assertIsFocused()
        compose.onNodeWithTag("c").performKeyPress(keyDown(AndroidKeyEvent.KEYCODE_DPAD_RIGHT))
        compose.waitForIdle()
        compose.onNodeWithTag("a").assertIsFocused()
    }

    /** NAV-005: and Left at the first tile wraps to the last. */
    @Test
    fun railWrapsAtItsLeadingEdge() {
        val labels = listOf("a", "b", "c")
        compose.setContent {
            ShoumeiTelevisionTheme {
                val requesters = remember { labels.map { FocusRequester() } }
                val listState = rememberLazyListState()
                LazyRow(state = listState) {
                    items(labels.size) { index ->
                        TelevisionFocusSurface(
                            onClick = {},
                            focusRequester = requesters[index],
                            modifier = Modifier
                                .testTag(labels[index])
                                .televisionHorizontalWrap(index, requesters, listState),
                        ) { Text(labels[index]) }
                    }
                }
            }
        }

        compose.onNodeWithTag("a").requestFocus()
        compose.onNodeWithTag("a").performKeyPress(keyDown(AndroidKeyEvent.KEYCODE_DPAD_LEFT))
        compose.waitForIdle()
        compose.onNodeWithTag("c").assertIsFocused()
    }

    /**
     * NAV-006: the control the viewer is standing on disappears, and focus goes to the successor
     * rather than nowhere. This is the shape of every Retry button on every async screen.
     */
    @Test
    fun removingTheFocusedControlHandsFocusToItsSuccessor() {
        compose.setContent {
            ShoumeiTelevisionTheme {
                var retryPresent by remember { mutableStateOf(true) }
                var retryFocused by remember { mutableStateOf(false) }
                val successor = remember { FocusRequester() }

                TelevisionFocusHandoff(
                    present = retryPresent,
                    focused = retryFocused,
                    successor,
                )
                Column {
                    TelevisionFocusSurface(
                        onClick = {},
                        focusRequester = successor,
                        modifier = Modifier.testTag("successor"),
                    ) { Text("successor") }
                    if (retryPresent) {
                        TelevisionFocusSurface(
                            onClick = { retryPresent = false },
                            onFocusChanged = { retryFocused = it },
                            modifier = Modifier.testTag("retry"),
                        ) { Text("retry") }
                    }
                }
            }
        }

        compose.onNodeWithTag("retry").requestFocus()
        compose.onNodeWithTag("retry").assertIsFocused()
        compose.onNodeWithTag("retry").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("successor").assertIsFocused()
    }

    /**
     * The other half of the same contract: a viewer who had already moved away is left alone when
     * the control disappears. Without this, the handoff would yank focus back on every state change.
     */
    @Test
    fun removingAnUnfocusedControlLeavesFocusAlone() {
        compose.setContent {
            ShoumeiTelevisionTheme {
                var retryPresent by remember { mutableStateOf(true) }
                var retryFocused by remember { mutableStateOf(false) }
                val successor = remember { FocusRequester() }

                TelevisionFocusHandoff(
                    present = retryPresent,
                    focused = retryFocused,
                    successor,
                )
                Row {
                    TelevisionFocusSurface(
                        onClick = {},
                        focusRequester = successor,
                        modifier = Modifier.testTag("successor"),
                    ) { Text("successor") }
                    TelevisionFocusSurface(
                        onClick = {},
                        modifier = Modifier.testTag("elsewhere"),
                    ) { Text("elsewhere") }
                    if (retryPresent) {
                        TelevisionFocusSurface(
                            onClick = {},
                            onFocusChanged = { retryFocused = it },
                            modifier = Modifier.testTag("retry"),
                        ) { Text("retry") }
                    }
                    TelevisionFocusSurface(
                        onClick = { retryPresent = false },
                        modifier = Modifier.testTag("remove"),
                    ) { Text("remove") }
                }
            }
        }

        compose.onNodeWithTag("retry").requestFocus()
        compose.onNodeWithTag("elsewhere").requestFocus()
        compose.onNodeWithTag("elsewhere").assertIsFocused()
        compose.onNodeWithTag("remove").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("successor").assertIsNotFocused()
    }
}

package com.maik205.shoumeiplayer.ui.screens.player

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.maik205.shoumeiplayer.ui.components.ErrorView
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.theme.Dimens
import kotlinx.coroutines.delay

/**
 * Full-bleed video surface + the two-layer OSD of docs/osd-v3.md §1.
 *
 * The [SurfaceView] is handed to the [PlayerViewModel]/`PlayerEngine` via [SurfaceHolder.Callback];
 * everything else on this screen is *input plumbing*. The interaction itself — which layer is up,
 * which OSD row has focus, whether a scrub is in flight, what BACK means right now — lives in the
 * pure reducer in PlayerInput.kt, so it can be tested without a device. This function does three
 * things with it: map key events onto [PlayerKey], run the [PlayerEffect]s it returns, and own the
 * two timers (§1's 5s auto-hide and the 1.5s mini-strip fade) that no pure function can own.
 *
 * Every entry into the overlay is an explicit focus handoff through [PlayerOsdFocus]: a directional
 * focus search launched from this full-screen root can never find a control inside the root's own
 * bounds, so "UP reveals the OSD" has to *mean* "request focus on play/pause".
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun PlayerScreen(
    itemId: String,
    startPositionTicks: Long,
    onExit: () -> Unit,
    /** §5 — a "More like this" card leaves the player for that item's detail screen. */
    onNavigateToItem: (itemId: String) -> Unit = {},
    /** §5 — a Cast portrait leaves the player for a person-filtered library grid. */
    onNavigateToPerson: (personId: String, name: String) -> Unit = { _, _ -> },
) {
    val viewModel = containerViewModel { container ->
        PlayerViewModel(
            engine = container.playerEngine,
            playbackRepository = container.playbackRepository,
            libraryRepository = container.libraryRepository,
            authRepository = container.authRepository,
            imageUrlBuilder = container.imageUrlBuilder,
            reporter = container.progressReporter,
            itemId = itemId,
            startPositionTicks = startPositionTicks,
            teardownScope = container.applicationScope,
        )
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // The whole §1/§3 interaction, in one immutable value.
    var input by remember { mutableStateOf(PlayerInputState()) }

    val rootFocusRequester = remember { FocusRequester() }
    val osdFocus = remember { PlayerOsdFocus() }

    // A focus handoff cannot happen inside the reducer *or* inside the key handler: the target may
    // only be composed in a later frame (a shelf row that the same press just opened). So focus
    // requests are queued and flushed from a LaunchedEffect. The token makes a repeat request to the
    // same target fire again.
    var focusTarget by remember { mutableStateOf<FocusTarget?>(null) }
    var focusToken by remember { mutableStateOf(0) }

    /** §1 — the layer-0 seek strip: slim bar only, gone [MiniStripFadeMs] after the last press. */
    var miniStripVisible by remember { mutableStateOf(false) }

    /** The OSD is shown once, unprompted, as soon as there is something to show it about. */
    var openedOnce by remember { mutableStateOf(false) }

    fun context() = PlayerInputContext(
        positionMs = uiState.positionMs,
        durationMs = uiState.durationMs,
        chapters = uiState.chapters,
        upNextVisible = uiState.upNextVisible,
        // The shelf is lazily loaded, so its emptiness right now says nothing; Cast, by contrast,
        // rides along on the item detail and is genuinely known.
        hasMoreLikeThis = true,
        hasCast = uiState.cast.isNotEmpty(),
    )

    fun runEffect(effect: PlayerEffect) {
        when (effect) {
            PlayerEffect.TogglePlayPause -> viewModel.togglePlayPause()
            is PlayerEffect.SeekBy -> viewModel.seekBy(effect.deltaMs)
            is PlayerEffect.SeekTo -> viewModel.seekTo(effect.positionMs)
            PlayerEffect.LoadShelves -> viewModel.loadShelves()
            PlayerEffect.DismissUpNext -> viewModel.dismissUpNext()
            PlayerEffect.Exit -> onExit()
            is PlayerEffect.Focus -> {
                focusTarget = effect.target
                focusToken++
            }
        }
    }

    fun dispatch(key: PlayerKey, eventTimeMs: Long = 0L, repeat: Boolean = false): Boolean {
        val result = reducePlayerKey(
            state = input,
            key = key,
            ctx = context(),
            eventTimeMs = eventTimeMs,
            repeat = repeat,
        )
        input = result.state
        result.effects.forEach(::runEffect)
        return result.consumed
    }

    LaunchedEffect(focusToken) {
        val target = focusTarget ?: return@LaunchedEffect
        val requester = when (target) {
            FocusTarget.Root -> rootFocusRequester
            FocusTarget.PlayPause -> osdFocus.playPause
            FocusTarget.SeekBar -> osdFocus.seekBar
            FocusTarget.UpNext -> osdFocus.upNext
            FocusTarget.MoreLikeThisRow -> osdFocus.moreLikeThis
            FocusTarget.CastRow -> osdFocus.cast
        }
        // A requester whose node has not been composed yet throws; the row it belongs to owns a
        // focusRestorer, so losing the race is survivable and never worth crashing playback over.
        runCatching { requester.requestFocus() }
    }

    LaunchedEffect(Unit) { rootFocusRequester.requestFocus() }

    // §1 — the mini strip is a fade, not a state machine: every seek key restarts the clock.
    LaunchedEffect(input.miniStripTick) {
        if (input.miniStripTick > 0) {
            miniStripVisible = true
            delay(MiniStripFadeMs)
            miniStripVisible = false
        }
    }

    // §1 — auto-hide 5s, restarted by every key that reaches the OSD (interactionTick) and suspended
    // while a panel, a scrub, or the shelf is holding the user still.
    LaunchedEffect(input.osdVisible, input.interactionTick, input.autoHideSuspended) {
        if (input.osdVisible && !input.autoHideSuspended) {
            delay(OsdAutoHideMs)
            input = input.copy(osdVisible = false, row = OsdRow.Controls, upNextFocused = false, virtualDirty = false)
            // §1 — "When OSD hides, focus returns to root".
            focusTarget = FocusTarget.Root
            focusToken++
        }
    }

    // Show the plate once the stream has actually resolved: revealing it while the screen is still a
    // black frame would hand focus to controls that are not composed yet.
    LaunchedEffect(uiState.loading, uiState.error) {
        if (!openedOnce && !uiState.loading && uiState.error == null) {
            openedOnce = true
            dispatch(PlayerKey.Menu)
        }
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.onStopped() }
    }

    BackHandler { dispatch(PlayerKey.Back) }

    val callbacks = PlayerOsdCallbacks(
        onPlayPause = { input = input.noteInteraction(); viewModel.togglePlayPause() },
        onSeekBy = { delta -> input = input.noteInteraction(); viewModel.seekBy(delta) },
        onPreviousEpisode = { input = input.noteInteraction(); viewModel.playPreviousEpisode() },
        onNextEpisode = { input = input.noteInteraction(); viewModel.playNextEpisode() },
        onOpenPanel = { panel -> input = input.copy(panel = panel).noteInteraction() },
        onSelectTrack = { track -> viewModel.selectTrack(track); input = input.copy(panel = null).noteInteraction() },
        onSelectSpeed = { speed -> viewModel.setSpeed(speed); input = input.copy(panel = null).noteInteraction() },
        onSelectQuality = { quality -> viewModel.setQuality(quality); input = input.copy(panel = null).noteInteraction() },
        onPlayUpNext = { viewModel.playUpNext() },
        // §5 — navigating away disposes this screen, and onDispose is what reports the stop and
        // tears the transcode down, so "stop playback cleanly" needs nothing extra here.
        onOpenItem = onNavigateToItem,
        onOpenPerson = { person -> onNavigateToPerson(person.id, person.name) },
        onFocusMoved = { target -> input = syncFocus(input, target).noteInteraction() },
        onInteraction = { input = input.noteInteraction() },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                val native = keyEvent.nativeKeyEvent
                val playerKey = when (keyEvent.key) {
                    Key.DirectionLeft -> PlayerKey.Left
                    Key.DirectionRight -> PlayerKey.Right
                    Key.DirectionUp -> PlayerKey.Up
                    Key.DirectionDown -> PlayerKey.Down
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> PlayerKey.Center
                    Key.Menu -> PlayerKey.Menu
                    // BACK arrives at the BackHandler, which owns the §1 cascade; taking it here too
                    // would run the cascade twice for one press.
                    Key.Back -> return@onPreviewKeyEvent false
                    else -> PlayerKey.Other
                }
                when (native.action) {
                    android.view.KeyEvent.ACTION_UP -> {
                        // §3 — releasing LEFT/RIGHT ends the hold, so the next one starts at 10s again.
                        if (playerKey == PlayerKey.Left || playerKey == PlayerKey.Right) {
                            input = releaseSeekHold(input)
                        }
                        false
                    }
                    android.view.KeyEvent.ACTION_DOWN -> dispatch(
                        key = playerKey,
                        eventTimeMs = native.eventTime,
                        repeat = native.repeatCount > 0,
                    )
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            viewModel.setSurface(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            viewModel.setSurfaceSize(width, height)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            viewModel.setSurface(null)
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        when {
            // §6 — loading is a skeleton of the content, and the skeleton of a video frame is the
            // black frame itself: no word, no spinner. The indeterminate hairline at y=0 covers
            // both halves of "no picture yet" (resolving the stream, and the engine opening it),
            // so the loading and buffering states read as one continuous signal — the second half
            // inside the OSD, via `bufferingActive`, which also owns PlayerState.Loading. Gating
            // this branch on the engine state too would unmount the OSD on every §5 in-place swap.
            showsBareFrame(uiState) -> BufferingLine(
                active = true,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            uiState.error != null -> ErrorView(
                message = uiState.error ?: "",
                onRetry = onExit,
                modifier = Modifier.padding(start = Dimens.OverscanHorizontal, top = 220.dp),
            )

            else -> PlayerOsd(
                state = uiState,
                visible = input.osdVisible,
                // The strip belongs to layer 0 only: reveal the OSD mid-fade and the real bar takes over.
                miniStripVisible = miniStripVisible && !input.osdVisible,
                row = input.row,
                panel = input.panel,
                upNextFocused = input.upNextFocused,
                // §3 — the trickplay preview shows exactly while the virtual playhead differs from live.
                scrubbing = input.scrubbing && input.virtualDirty,
                scrubPositionMs = input.displayPositionMs(uiState.positionMs),
                focus = osdFocus,
                callbacks = callbacks,
            )
        }
    }
}

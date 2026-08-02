package com.maik205.shoumeiplayer.ui.television.screens.screensaver

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.format.DateFormat
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.Date

private const val SCREENSAVER_FADE_IN_MS = 900
private const val SCREENSAVER_FADE_OUT_MS = 240
private const val SLIDE_CROSSFADE_MS = 900
private const val CLOCK_TICK_MILLIS = 10_000L

/**
 * Wraps the whole television UI with idle detection and the screensaver overlay (#87).
 *
 * Deliberately structured so neither a keypress nor a slide change recomposes this body: the key
 * handler reads `state.value` (a plain read, not a snapshot subscription), the idle watchdog
 * collects the state flow inside a `LaunchedEffect`, and only [ScreensaverOverlay] -- a sibling of
 * [content] -- subscribes to it. Were the state read here instead, every remote keypress and every
 * slide change would recompose the entire `NavHost` underneath. The one subscription that does live
 * here is the lifecycle state, which changes a handful of times per session.
 *
 * Focus is never touched. The overlay is drawn over the screen the viewer left, takes no focus of
 * its own, and consumes the key that dismisses it, so the dismissing press cannot also act on
 * whatever was underneath and the viewer lands back exactly where they were.
 */
@Composable
fun ScreensaverHost(content: @Composable () -> Unit) {
    val viewModel = containerViewModel { container ->
        ScreensaverViewModel(
            catalog = container.mediaCatalog,
            settingsStore = container.settingsStore,
        )
    }
    val activity = LocalContext.current.findActivity()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val resumed = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    val interactions = remember { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onPreviewKeyEvent {
                if (viewModel.state.value.visible) {
                    viewModel.dismiss()
                    // Any remote key wakes the screen and does nothing else.
                    true
                } else {
                    interactions.tryEmit(Unit)
                    false
                }
            },
    ) {
        content()
        ScreensaverOverlay(viewModel)
    }

    LaunchedEffect(viewModel, activity, resumed) {
        // Idle time only means anything while the viewer is in front of this activity. Left
        // ungated, the watchdog keeps counting after the app is backgrounded, fetches artwork
        // nobody asked for, and greets the viewer with a stale slide on the way back in.
        if (!resumed) {
            viewModel.dismiss()
            return@LaunchedEffect
        }
        viewModel.state
            .map { it.timeoutMinutes to it.visible }
            .distinctUntilChanged()
            .collectLatest { (timeoutMinutes, visible) ->
                if (visible || screensaverIdleTimeoutMillis(timeoutMinutes) == null) {
                    return@collectLatest
                }
                var idleMillis = 0L
                val interactionResets = launch { interactions.collect { idleMillis = 0L } }
                try {
                    while (true) {
                        delay(SCREENSAVER_IDLE_TICK_MILLIS)
                        val holdsScreenOn = windowHoldsScreenOn(activity)
                        idleMillis = screensaverIdleAfterTick(
                            idleMillis = idleMillis,
                            tickMillis = SCREENSAVER_IDLE_TICK_MILLIS,
                            playbackHoldsScreenOn = holdsScreenOn,
                        )
                        if (shouldStartScreensaver(timeoutMinutes, idleMillis, holdsScreenOn)) {
                            viewModel.start()
                            // Re-arm rather than stop: when the pool is empty or the server is
                            // unreachable, `start()` shows nothing, and this has to try again a
                            // timeout later instead of going inert until the next keypress.
                            idleMillis = 0L
                        }
                    }
                } finally {
                    interactionResets.cancel()
                }
            }
    }
}

/**
 * True while something holds `FLAG_KEEP_SCREEN_ON` on the activity window.
 *
 * This is the player's own Ambient-Mode suppression read back, not a reimplementation of it:
 * `TelevisionPlayerContent` adds the flag exactly when `shouldKeepScreenOn(audio, state)` is true
 * and clears it on dispose. Reading the flag rather than plumbing a second "is playing" signal
 * means the two can never disagree. See [shouldStartScreensaver] for the audio-only case.
 */
internal fun windowHoldsScreenOn(activity: Activity?): Boolean {
    val flags = activity?.window?.attributes?.flags ?: return false
    return flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
}

private fun Context.findActivity(): Activity? {
    var context: Context? = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@Composable
private fun ScreensaverOverlay(viewModel: ScreensaverViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.visible, state.slideSerial, state.imageDurationMillis) {
        if (!state.visible) return@LaunchedEffect
        delay(state.imageDurationMillis)
        viewModel.advance()
    }

    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(tween(SCREENSAVER_FADE_IN_MS)),
        exit = fadeOut(tween(SCREENSAVER_FADE_OUT_MS)),
    ) {
        state.slide?.let { slide ->
            ScreensaverSlideContent(slide = slide, showClock = state.showClock)
        }
    }
}

@Composable
private fun ScreensaverSlideContent(slide: ScreensaverSlide, showClock: Boolean) {
    val colors = TelevisionTheme.colors
    val platformContext = LocalPlatformContext.current
    val request = remember(slide.imageUrl, platformContext) {
        ImageRequest.Builder(platformContext)
            .data(slide.imageUrl)
            // A long dissolve rather than a cut, so consecutive slides never land hard on the same
            // pixels -- the panel is the thing this feature exists to protect.
            .crossfade(SLIDE_CROSSFADE_MS)
            .build()
    }

    // Announced as the screensaver rather than as a stray movie title, and it says how to get out:
    // a viewer who cannot see the artwork has no other way to tell why the screen changed.
    val announcement = stringResource(R.string.tv_screensaver_showing, slide.title)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.Black)
            .semantics { contentDescription = announcement },
    ) {
        AsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to colors.Black.copy(alpha = 0.46f),
                        0.34f to colors.Black.copy(alpha = 0.10f),
                        1f to colors.Black.copy(alpha = 0.82f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.6f)
                .padding(
                    start = TelevisionDimensions.SafeHorizontal,
                    end = TelevisionDimensions.SafeHorizontal,
                    bottom = 40.dp,
                ),
        ) {
            Text(
                text = slide.title,
                style = MaterialTheme.typography.headlineMedium,
                color = colors.Paper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            slide.subtitle?.takeIf(String::isNotBlank)?.let { subtitle ->
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.PaperMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (showClock) {
            ScreensaverClock(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 32.dp, end = TelevisionDimensions.SafeHorizontal),
            )
        }
    }
}

/**
 * The optional clock overlay.
 *
 * Formatted with the platform's own time format so it follows the television's 12/24-hour and
 * locale settings, and ticked coarsely -- a screensaver clock that redraws every second would keep
 * the CPU busy for no visible gain, since only the minute is shown.
 */
@Composable
private fun ScreensaverClock(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val formatter = remember(context) { DateFormat.getTimeFormat(context) }
    var now by remember { mutableStateOf(Date()) }

    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(CLOCK_TICK_MILLIS)
        }
    }

    Text(
        text = formatter.format(now),
        modifier = modifier,
        style = MaterialTheme.typography.displaySmall,
        color = TelevisionTheme.colors.Paper,
    )
}

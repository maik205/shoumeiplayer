package com.maik205.shoumeiplayer.ui.screens.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maik205.shoumeiplayer.player.PlaybackSpeed
import com.maik205.shoumeiplayer.player.PlayerState
import com.maik205.shoumeiplayer.player.VideoQuality
import com.maik205.shoumeiplayer.ui.components.SpecSeparator
import com.maik205.shoumeiplayer.ui.components.SpecTab
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Ease
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Scrims
import com.maik205.shoumeiplayer.ui.theme.ShoumeiType
import com.maik205.shoumeiplayer.ui.theme.Tungsten
import kotlinx.coroutines.delay

// --- geometry ----------------------------------------------------------------------------------

/** §4 — the logo block's ceiling; a taller logo is scaled down, never cropped. */
private val LogoMaxHeight = 96.dp

/** The bottom plate: identity block, meta line, seek bar and the control track, over [Scrims.OsdBottom]. */
private val BottomPlateHeight = 320.dp

/** §1 — the layer-0 mini strip's own plate: just tall enough to read timecodes over bright video. */
private val MiniStripPlateHeight = 120.dp

/** Vertical rhythm inside the bottom plate. */
private val IdentityToMeta = 10.dp
private val MetaToBar = 22.dp
private val BarToTrack = 26.dp

/** §5.3 — buffering hairline: 2dp at y=0, 0.25 ↔ 0.85 on a 900ms loop, delayed 400ms. */
private val BufferingLineHeight = 2.dp
private const val BUFFERING_MIN_ALPHA = 0.25f
private const val BUFFERING_MAX_ALPHA = 0.85f

/** How long a non-fatal notice (a refused quality swap, an episode that would not resolve) stays up. */
private const val NOTICE_HOLD_MS = 4_000L

/** §2 — the step the ∓10s chips carry. LEFT/RIGHT scrubbing is [PlayerScreen]'s, per §3. */
internal const val SeekChipStepMs = SeekStepMs

/** The label a subtitle chip and its panel both use for "no subtitles". */
internal const val OffLabel = "Off"

// --- the OSD ------------------------------------------------------------------------------------

/**
 * docs/osd-v3.md — Player OSD v3: a YouTube-TV chip track over one gradient plate.
 *
 * **The seam.** [state] is the ViewModel's own [PlayerUiState], passed through unchanged: the OSD
 * needs identity, tracks, chapters, speed, quality, trickplay, adjacency, Up Next and both shelves,
 * and re-declaring all of that as twenty parameters would only make a second place for it to drift.
 * Everything else here is the *screen's* input model ([PlayerInputState]) projected one field at a
 * time — [visible], [miniStripVisible], [row], [panel], [upNextFocused], [scrubbing],
 * [scrubPositionMs] — plus the two bundles of [PlayerOsdContract.kt][PlayerOsdFocus]:
 * [focus] (where the screen can send focus) and [callbacks] (what the OSD can ask for).
 *
 * The OSD runs no key handling of its own. It draws what it is told and reports focus back through
 * [PlayerOsdCallbacks.onFocusMoved], which is what keeps §1's BACK ladder — panel → Up Next → shelf
 * → cancel scrub → hide OSD → exit — one state machine in [PlayerInput.kt] instead of two.
 *
 * **What it draws** (bottom-up, all over [Scrims.OsdBottom], content at 100% opacity):
 * - §4 identity: the item's Logo image (the *series* logo for an episode) capped at 96dp with a text
 *   title fallback when it is absent or fails, then `S3 · E06    Immolation` — or `2019 · 1080p` for
 *   a movie — with `1.5×` appended on a tab column whenever the rate is not 1×.
 * - §3 row 0: the slim seek bar, timecodes at its ends, chapter ticks, buffered segment, and the
 *   trickplay preview floating above the scrubber while the virtual playhead differs from live.
 * - §2 row 1: ONE horizontal control track in two clusters — transport left, options right — of 42dp
 *   chips that expand into labelled white pills on focus, each with a 4dp tungsten dot when its
 *   feature is active.
 *
 * Over the plate: the §5 Up Next card (bottom-right, episodes only), the §5 shelf (rows 2 and 3, a
 * bottom sheet the video keeps playing behind, opened by [row]), the §2 options panel, and the
 * buffering hairline — which outlives the OSD, because it is about the picture, not the chrome.
 *
 * Layer 0 (§1) is [miniStripVisible]: LEFT/RIGHT on the bare surface raises the slim strip alone.
 */
@Suppress("LongParameterList")
@Composable
fun PlayerOsd(
    state: PlayerUiState,
    visible: Boolean,
    miniStripVisible: Boolean,
    row: OsdRow,
    panel: PlayerPanel?,
    upNextFocused: Boolean,
    scrubbing: Boolean,
    scrubPositionMs: Long,
    focus: PlayerOsdFocus,
    callbacks: PlayerOsdCallbacks,
    modifier: Modifier = Modifier,
) {
    val duration = state.durationMs ?: 0L
    // §1 — the shelf *is* rows 2 and 3: there is no separate "shelf open" flag to fall out of step
    // with the row model.
    val shelfOpen = visible && (row == OsdRow.MoreLikeThis || row == OsdRow.Cast)
    // The card stays up while it holds focus even if the playhead has ticked out of the §5 window,
    // so a stray progress update can never yank focus out from under the user.
    val upNext = state.upNext

    Box(modifier = modifier.fillMaxSize()) {
        // Layer 0 — the slim strip alone. Suppressed while the plate is up so the bar is never drawn
        // twice; the screen already gates this, and this is the belt to that pair of braces.
        AnimatedVisibility(
            visible = miniStripVisible && !visible,
            enter = fadeIn(tween(Dur.OsdIn, easing = Ease.Decel)),
            exit = fadeOut(tween(Dur.OsdOut, easing = Ease.Accel)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            MiniStrip(state = state, durationMs = duration)
        }

        // Layer 1 — the plate.
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(Dur.OsdIn, easing = Ease.Decel)) +
                slideInVertically(tween(Dur.OsdIn, easing = Ease.Decel)) { it / 6 },
            exit = fadeOut(tween(Dur.OsdOut, easing = Ease.Accel)) +
                slideOutVertically(tween(Dur.OsdOut, easing = Ease.Accel)) { it / 12 },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomPlate(
                state = state,
                row = row,
                scrubbing = scrubbing,
                headMs = scrubPositionMs,
                durationMs = duration,
                focus = focus,
                callbacks = callbacks,
            )
        }

        // §5 — the Up Next card. It rides above the plate while the plate is up and sits on the
        // overscan line when it is not, so it never lands on top of the control track.
        if (upNext != null && (state.upNextVisible || upNextFocused)) {
            UpNextCard(
                upNext = upNext,
                remainingMs = (duration - state.positionMs).coerceAtLeast(0L),
                onPlay = {
                    callbacks.onInteraction()
                    callbacks.onPlayUpNext()
                },
                focusRequester = focus.upNext,
                onFocused = { callbacks.onFocusMoved(FocusTarget.UpNext) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = Dimens.OverscanHorizontal,
                        bottom = if (visible) BottomPlateHeight + 12.dp else Dimens.OverscanVertical,
                    ),
            )
        }

        // §5 — rows 2 and 3, sliding over the plate.
        PlayerShelf(
            similar = state.similar,
            cast = state.cast,
            loading = state.shelvesLoading,
            onSimilarClick = { card ->
                callbacks.onInteraction()
                callbacks.onOpenItem(card.id)
            },
            onCastClick = { person ->
                callbacks.onInteraction()
                callbacks.onOpenPerson(person)
            },
            visible = shelfOpen,
            similarRowFocus = focus.moreLikeThis,
            castRowFocus = focus.cast,
            onFocusMoved = callbacks.onFocusMoved,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // §2 — the panel a chip opened. Composed unconditionally so its slide-out has something to
        // animate; `panel == null` is simply "not visible".
        OsdPanelSheet(
            title = panel.title(),
            items = panelItems(panel, state),
            onSelect = { item ->
                callbacks.onInteraction()
                applyPanelSelection(panel, item, state, callbacks)
            },
            visible = panel != null,
            modifier = Modifier.align(Alignment.CenterEnd),
        )

        // §5.3 — buffering is a hairline at y=0, never a spinner over video; it outlives the OSD.
        BufferingLine(
            active = bufferingActive(state),
            modifier = Modifier.align(Alignment.TopCenter),
        )

        NoticeBanner(
            notice = state.notice,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = Dimens.OverscanHorizontal, top = Dimens.OverscanVertical + 12.dp),
        )
    }
}

// --- layer 0 -------------------------------------------------------------------------------------

/**
 * §1 — layer 0's slim strip: the bar and its timecodes, nothing else. No chips, no identity, no
 * focus. LEFT/RIGHT on the bare surface is a seek, not a summons, and the strip says only where the
 * seek landed.
 */
@Composable
private fun MiniStrip(state: PlayerUiState, durationMs: Long) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MiniStripPlateHeight)
            .background(Scrims.OsdBottom),
    ) {
        OsdSeekBar(
            headMs = state.positionMs,
            liveMs = state.positionMs,
            durationMs = durationMs,
            bufferedMs = state.bufferedMs,
            chapters = state.chapters,
            focused = false,
            scrubbing = false,
            showPreview = false,
            // Layer 0 belongs to the root surface: the strip is a readout, never a focus target.
            focusable = false,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    start = Dimens.OverscanHorizontal,
                    end = Dimens.OverscanHorizontal,
                    bottom = Dimens.OverscanVertical,
                ),
        )
    }
}

// --- layer 1 -------------------------------------------------------------------------------------

@Composable
private fun BottomPlate(
    state: PlayerUiState,
    row: OsdRow,
    scrubbing: Boolean,
    headMs: Long,
    durationMs: Long,
    focus: PlayerOsdFocus,
    callbacks: PlayerOsdCallbacks,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = BottomPlateHeight)
            .background(Scrims.OsdBottom)
            .padding(
                start = Dimens.OverscanHorizontal,
                end = Dimens.OverscanHorizontal,
                top = 24.dp,
                bottom = Dimens.OverscanVertical,
            ),
        verticalArrangement = Arrangement.Bottom,
    ) {
        IdentityBlock(state = state)
        Spacer(modifier = Modifier.height(MetaToBar))
        OsdSeekBar(
            headMs = headMs,
            liveMs = state.positionMs,
            durationMs = durationMs,
            bufferedMs = state.bufferedMs,
            chapters = state.chapters,
            focused = row == OsdRow.Seek,
            scrubbing = scrubbing,
            trickplay = state.trickplay,
            focusRequester = focus.seekBar,
            onFocused = { callbacks.onFocusMoved(FocusTarget.SeekBar) },
        )
        Spacer(modifier = Modifier.height(BarToTrack))
        ControlTrack(state = state, focus = focus, callbacks = callbacks)
    }
}

/**
 * §4 — the identity block: logo (or title) over the season/episode line.
 *
 * The logo is the series' for an episode and the item's own for a movie
 * ([com.maik205.shoumeiplayer.data.ImageUrlBuilder.logoWithParentFallback] already resolves that);
 * the text title stands in when it is absent *or* when it fails to load, which is why the failure is
 * tracked rather than left to Coil's silently empty frame.
 */
@Composable
private fun IdentityBlock(state: PlayerUiState) {
    var logoFailed by remember(state.logoUrl) { mutableStateOf(false) }
    val platformContext = LocalPlatformContext.current

    Column {
        if (state.logoUrl != null && !logoFailed) {
            AsyncImage(
                model = remember(state.logoUrl, platformContext) {
                    ImageRequest.Builder(platformContext)
                        .data(state.logoUrl)
                        .crossfade(true)
                        .build()
                },
                contentDescription = identityTitle(state),
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomStart,
                onError = { logoFailed = true },
                modifier = Modifier.heightIn(max = LogoMaxHeight),
            )
        } else {
            Text(
                text = identityTitle(state),
                style = MaterialTheme.typography.displaySmall,
                color = Paper,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(fraction = 0.7f),
            )
        }

        val meta = identityMetaLine(state)
        val code = episodeCode(state)
        if (meta.isNotBlank() || code != null) {
            Spacer(modifier = Modifier.height(IdentityToMeta))
            Row(verticalAlignment = Alignment.Bottom) {
                // §4 — the season/episode code is clock-like structure, so it takes the mono face;
                // the episode's own name beside it stays on the app's one sans family.
                if (code != null) {
                    Text(text = code, style = ShoumeiType.Duration, color = Paper, maxLines = 1)
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Paper.copy(alpha = Alpha.TextTertiary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * §2 — the control track: ONE horizontal row, two clusters, LEFT/RIGHT walking across the gap.
 *
 * ```
 * [prev-ep] [-10s] [play/pause] [+10s] [next-ep]   ···   [subs] [audio] [speed] [quality]
 * ```
 * The prev/next chips exist for episodes only. They are drawn for *every* episode, including the
 * first and last of a series, and simply do nothing at the ends: adjacency is fetched
 * asynchronously, so hiding them on a null neighbour would shuffle the whole track — and move
 * play/pause out from under the user's thumb — a second after the OSD appeared.
 *
 * Every chip reports [FocusTarget.PlayPause] when it takes focus. That target is not "the play
 * button" but "focus is in row 1": `syncFocus` maps it to `row = Controls`, which is exactly what a
 * chip taking focus on its own means, whichever chip it was.
 */
@Composable
private fun ControlTrack(
    state: PlayerUiState,
    focus: PlayerOsdFocus,
    callbacks: PlayerOsdCallbacks,
) {
    fun press(action: () -> Unit): () -> Unit = {
        callbacks.onInteraction()
        action()
    }

    val enterRow = { callbacks.onFocusMoved(FocusTarget.PlayPause) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChipTrackHeight),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
            if (state.isEpisode) {
                OsdChip(
                    label = "Previous episode",
                    imageVector = PlayerIcons.PreviousEpisode,
                    active = false,
                    onClick = press(callbacks.onPreviousEpisode),
                    onFocused = enterRow,
                )
            }
            OsdChip(
                label = "Back 10 seconds",
                active = false,
                onClick = press { callbacks.onSeekBy(-SeekChipStepMs) },
                onFocused = enterRow,
            ) { tint -> SeekChipGlyph(forward = false, tint = tint) }

            val playing = state.state == PlayerState.Playing
            OsdChip(
                label = if (playing) "Pause" else "Play",
                active = false,
                onClick = press(callbacks.onPlayPause),
                focusRequester = focus.playPause,
                onFocused = enterRow,
            ) { tint ->
                Crossfade(
                    targetState = playing,
                    animationSpec = tween(Dur.GlyphFade),
                    label = "playPauseGlyph",
                ) { isPlaying ->
                    Icon(
                        imageVector = if (isPlaying) PlayerIcons.Pause else Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(ChipIconSize),
                    )
                }
            }

            OsdChip(
                label = "Forward 10 seconds",
                active = false,
                onClick = press { callbacks.onSeekBy(SeekChipStepMs) },
                onFocused = enterRow,
            ) { tint -> SeekChipGlyph(forward = true, tint = tint) }

            if (state.isEpisode) {
                OsdChip(
                    label = "Next episode",
                    imageVector = PlayerIcons.NextEpisode,
                    active = false,
                    onClick = press(callbacks.onNextEpisode),
                    onFocused = enterRow,
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(ChipSpacing)) {
            val subtitle = state.subtitleTracks.firstOrNull { it.selected && it.id != -1 }
            OsdChip(
                label = chipLabel("Subtitles", subtitle?.label ?: OffLabel),
                imageVector = PlayerIcons.Subtitles,
                active = subtitle != null,
                onClick = press { callbacks.onOpenPanel(PlayerPanel.Subtitles) },
                onFocused = enterRow,
            )
            OsdChip(
                label = chipLabel("Audio", state.audioTracks.firstOrNull { it.selected }?.label),
                imageVector = PlayerIcons.Audio,
                active = false,
                onClick = press { callbacks.onOpenPanel(PlayerPanel.Audio) },
                onFocused = enterRow,
            )
            OsdChip(
                label = chipLabel("Speed", PlaybackSpeed.label(state.speed)),
                imageVector = PlayerIcons.Speed,
                active = !PlaybackSpeed.isNormal(state.speed),
                onClick = press { callbacks.onOpenPanel(PlayerPanel.Speed) },
                onFocused = enterRow,
            )
            OsdChip(
                label = chipLabel("Quality", state.quality.label),
                imageVector = PlayerIcons.Quality,
                active = state.quality != VideoQuality.AUTO,
                onClick = press { callbacks.onOpenPanel(PlayerPanel.Quality) },
                onFocused = enterRow,
            )
        }
    }
}

// --- panels ---------------------------------------------------------------------------------------

/** The panel's heading. Sentence case: the app has exactly one uppercase eyebrow and it is not here. */
private fun PlayerPanel?.title(): String = when (this) {
    null -> ""
    PlayerPanel.Subtitles -> "Subtitles"
    PlayerPanel.Audio -> "Audio"
    PlayerPanel.Speed -> "Speed"
    PlayerPanel.Quality -> "Quality"
}

/** §5 — what an open panel lists. Pure, so the rows and [applyPanelSelection] cannot drift apart. */
internal fun panelItems(panel: PlayerPanel?, state: PlayerUiState): List<OsdPanelItem> = when (panel) {
    null -> emptyList()
    PlayerPanel.Subtitles -> subtitleTracksWithOff(state.subtitleTracks, OffLabel).map { it.toPanelItem() }
    PlayerPanel.Audio -> state.audioTracks.map { it.toPanelItem() }
    PlayerPanel.Speed -> PlaybackSpeed.Steps.map { step ->
        OsdPanelItem(
            key = speedKey(step),
            label = PlaybackSpeed.label(step),
            selected = PlaybackSpeed.nearestStep(state.speed) == step,
        )
    }
    PlayerPanel.Quality -> VideoQuality.Ladder.map { rung ->
        OsdPanelItem(
            key = rung.name,
            label = rung.label,
            // The cap is the whole meaning of a rung, so the panel says it out loud rather than
            // leaving "1080p" to imply a bitrate the user cannot see.
            detail = rung.maxStreamingBitrate?.let { "${it / 1_000_000} Mbps" },
            selected = state.quality == rung,
        )
    }
}

private fun applyPanelSelection(
    panel: PlayerPanel?,
    item: OsdPanelItem,
    state: PlayerUiState,
    callbacks: PlayerOsdCallbacks,
) {
    when (panel) {
        null -> Unit
        PlayerPanel.Subtitles -> subtitleTracksWithOff(state.subtitleTracks, OffLabel)
            .firstOrNull { it.panelKey == item.key }
            ?.let(callbacks.onSelectTrack)
        PlayerPanel.Audio -> state.audioTracks.firstOrNull { it.panelKey == item.key }
            ?.let(callbacks.onSelectTrack)
        PlayerPanel.Speed -> PlaybackSpeed.Steps.firstOrNull { speedKey(it) == item.key }
            ?.let(callbacks.onSelectSpeed)
        PlayerPanel.Quality -> VideoQuality.entries.firstOrNull { it.name == item.key }
            ?.let(callbacks.onSelectQuality)
    }
}

/** Stable list identity for a speed rung. */
internal fun speedKey(step: Float): String = "speed:$step"

// --- identity helpers ------------------------------------------------------------------------------

/**
 * §4 — the text title: the *series* name for an episode (the logo would have been the series'), the
 * item's own name otherwise. Falls through to whatever exists, so the block is never blank.
 */
internal fun identityTitle(state: PlayerUiState): String = when {
    state.isEpisode -> state.seriesName?.takeIf { it.isNotBlank() } ?: state.title
    else -> state.title
}

/** §4 — `S3 · E06`, the mono half of the identity line. Null for movies and unnumbered episodes. */
internal fun episodeCode(state: PlayerUiState): String? {
    if (!state.isEpisode) return null
    val season = state.seasonNumber
    val episode = state.episodeNumber
    return when {
        season != null && episode != null -> "S$season${SpecSeparator}E$episode"
        episode != null -> "E$episode"
        else -> null
    }
}

/**
 * §4 — the sans half of the identity line, with the speed badge appended.
 *
 * For an episode that is its own name (`Immolation`); for a movie the `2019 · 1080p` year-and-quality
 * line. A rate other than 1× joins on the [SpecTab] column rather than as a third `·`, because
 * ui-design §3.2 allows a metadata line exactly one middle dot and the movie line has already spent
 * it. Quality only appears once it is a *choice*: at [VideoQuality.AUTO] the app does not know what
 * the server picked, and printing a guess would be worse than printing nothing.
 */
internal fun identityMetaLine(state: PlayerUiState): String {
    val head = if (state.isEpisode) {
        state.title
    } else {
        listOfNotNull(
            state.year?.toString(),
            state.quality.label.takeIf { state.quality != VideoQuality.AUTO },
        ).joinToString(SpecSeparator)
    }
    return specLineWithChapter(head, speedBadge(state.speed))
}

/** §4 — `1.5×`, or null at normal rate: a `1×` badge would be noise on every title in the library. */
internal fun speedBadge(speed: Float): String? =
    PlaybackSpeed.label(speed).takeIf { !PlaybackSpeed.isNormal(speed) }

/** §2 — a focused chip's inline label: `Subtitles · English`, or bare `Subtitles` with no value. */
internal fun chipLabel(name: String, value: String?): String =
    if (value.isNullOrBlank()) name else "$name$SpecSeparator$value"

/**
 * §3.2 — joins two halves of a metadata line on a tab column: `Immolation    1.5×`. Either half may
 * be absent, and a blank one never leaves a dangling separator.
 *
 * The tab rather than a second `·` is ui-design §3.2's rule: a line carries at most one middle dot,
 * and three or more fields take spacing instead.
 */
internal fun specLineWithChapter(specLine: String?, chapterName: String?): String =
    listOfNotNull(
        specLine?.takeIf { it.isNotBlank() },
        chapterName?.takeIf { it.isNotBlank() },
    ).joinToString(SpecTab)

// --- shared chrome ---------------------------------------------------------------------------------

/**
 * A non-fatal message about the last action — a quality swap the server refused, an episode that
 * would not resolve. It never replaces the video, and it never waits for the OSD: the action
 * happened whether or not the chrome was up, so the report does too.
 */
@Composable
private fun NoticeBanner(notice: String?, modifier: Modifier = Modifier) {
    var shown by remember(notice) { mutableStateOf(notice != null) }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(NOTICE_HOLD_MS)
            shown = false
        }
    }
    AnimatedVisibility(
        visible = shown && notice != null,
        enter = fadeIn(tween(Dur.OsdIn, easing = Ease.Decel)),
        exit = fadeOut(tween(Dur.OsdOut, easing = Ease.Accel)),
        modifier = modifier,
    ) {
        Text(
            text = notice.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = Tungsten,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Whether the buffering hairline should be pulsing: the engine is opening a file
 * ([PlayerState.Loading]), re-buffering, or a §5 stream swap is still resolving.
 *
 * `Loading` belongs here rather than to [PlayerScreen]'s bare-frame branch because
 * `PlayerEngine.load` raises it on *every* `load`, including the ones a §5 quality rung or episode
 * change issues. Treating it as "no OSD yet" would tear the whole overlay — chips, shelf, panel, and
 * every [PlayerOsdFocus] target — out of the tree in the middle of a swap that §5 requires to happen
 * *in place*, dropping focus while the reducer still believed it was on layer 1.
 */
internal fun bufferingActive(state: PlayerUiState): Boolean =
    state.state == PlayerState.Buffering || state.state == PlayerState.Loading || state.swapping

/**
 * Whether [PlayerScreen] shows the bare black frame instead of the OSD.
 *
 * Only the *initial* resolve qualifies: `PlayerUiState.loading` is false from the moment the first
 * stream resolves and never returns, so once there is an item to draw, the OSD stays mounted and
 * [bufferingActive]'s hairline carries every subsequent wait.
 */
internal fun showsBareFrame(state: PlayerUiState): Boolean = state.loading

/**
 * §5.3 / §7 — the only loop in the player: a 2dp full-width line at y=0 pulsing `0.25 ↔ 0.85` on a
 * 900ms cycle, delayed 400ms so brief stalls don't flash. No spinner over video, ever.
 *
 * It doubles as the player's loading state ([PlayerScreen]) and as the §5 stream-swap signal: a
 * quality change or an episode change keeps the outgoing picture on screen while the new stream
 * resolves, and the hairline is the honest way to say the app is genuinely waiting.
 */
@Composable
internal fun BufferingLine(active: Boolean, modifier: Modifier = Modifier) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(active) {
        if (active) {
            delay(Dur.BufferDelay.toLong())
            shown = true
        } else {
            shown = false
        }
    }
    if (!shown) return

    val transition = rememberInfiniteTransition(label = "bufferingPulse")
    val alpha by transition.animateFloat(
        initialValue = BUFFERING_MIN_ALPHA,
        targetValue = BUFFERING_MAX_ALPHA,
        animationSpec = infiniteRepeatable(
            animation = tween(Dur.BufferPulse, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "bufferingAlpha",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(BufferingLineHeight)
            .background(Color.White.copy(alpha = alpha)),
    )
}

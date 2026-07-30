package com.maik205.shoumeiplayer.ui.screens.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.ui.components.EmptyView
import com.maik205.shoumeiplayer.ui.components.ErrorView
import com.maik205.shoumeiplayer.ui.components.MediaRow
import com.maik205.shoumeiplayer.ui.components.NavRailDestination
import com.maik205.shoumeiplayer.ui.components.NavRailScaffold
import com.maik205.shoumeiplayer.ui.components.RowGlowBleed
import com.maik205.shoumeiplayer.ui.components.SkeletonRow
import com.maik205.shoumeiplayer.ui.components.SlabButton
import com.maik205.shoumeiplayer.ui.components.rememberBlurHashPainter
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.theme.Ash600
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Ease
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Scrims
import kotlin.math.abs

private const val ROW_MY_MEDIA = "my_media"

/** F7 — resolves a [HomeRow]'s display title here, at the one Composable call site, from whichever
 * of [HomeRow.titleRes] / [HomeRow.titleLiteral] the ViewModel populated. */
@Composable
private fun HomeRow.resolveTitle(): String {
    val res = titleRes
    return when {
        res != null && titleArg != null -> stringResource(res, titleArg)
        res != null -> stringResource(res)
        else -> titleLiteral.orEmpty()
    }
}

/** §5.1 — the hero art sits under type, so it never runs at full strength. */
private const val HERO_BACKDROP_ALPHA = 0.55f

/** The blurhash placeholder fades up from the item's own colour, a shade under the art it replaces. */
private const val HERO_BLURHASH_ALPHA = 0.45f

/** Compact top stage: logo, one metadata line and actions, followed immediately by the first row. */
private val HeroBlockHeight = 232.dp

/** §5.2 rhythm, borrowed: logo art never grows past the display line it replaces. */
private val HeroLogoMaxHeight = 72.dp

/**
 * Android TV's default focus relocation pins a focused child at 30% of its scroll container. On
 * Home that pulls row zero over the hero even though the row already fits. Use minimum-distance
 * relocation here: visible rows do not move, while lower rows still scroll into view.
 */
@OptIn(ExperimentalFoundationApi::class)
private object HomeBringIntoViewSpec : BringIntoViewSpec {
    override fun calculateScrollDistance(
        offset: Float,
        size: Float,
        containerSize: Float,
    ): Float {
        val trailingEdge = offset + size
        return when {
            offset >= 0f && trailingEdge <= containerSize -> 0f
            offset < 0f && trailingEdge > containerSize -> 0f
            abs(offset) < abs(trailingEdge - containerSize) -> offset
            else -> trailingEdge - containerSize
        }
    }
}

/**
 * Which branch of the content `when` owns `contentFocusRequester` this frame. M6.3 — the requester
 * is one node per state, never one node bound only to the happy path: in [Error] and [Empty] it is
 * the state view's slab, so arrival focus lands there and the rail's RIGHT target stays attached.
 * [Loading] composes a skeleton and nothing focusable, so it is the one state with no target.
 */
private enum class ContentFocusTarget { Loading, Error, Empty, Rows }

/**
 * §5.1 — Home v2: a billboard hero over lazy rows, with the collapsed nav rail on the left edge.
 *
 * The hero is driven by whichever card holds focus, debounced [Dur.HeroDebounce] in the ViewModel
 * so a held direction key sweeps a row without strobing the room. Guardrail 7 is structural here,
 * not incidental: `HomeScreen` itself never reads `viewModel.hero` — only [HeroBackdrop] and
 * [HeroCopy] collect it, so a card focus recomposes those two subtrees and nothing else. The rows
 * are a `LazyColumn` of `LazyRow`s and stay entirely out of it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToLibrary: (String, String, String?) -> Unit,
    onNavigate: (NavRailDestination) -> Unit = {},
) {
    val viewModel = containerViewModel { container ->
        HomeViewModel(container.libraryRepository, container.imageUrlBuilder)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    NavRailScaffold(
        selected = NavRailDestination.Home,
        // M-B14 — every screen hands the whole destination to `NavGraph`, which owns the one
        // `launchSingleTop` / `popUpTo(HomeRoute)` hop so rail moves never stack.
        onSelect = onNavigate,
    ) { contentFocusRequester, railFocusRequester ->
        // M6.3 — the first actionable element takes focus once it exists, in *every* state: card 0
        // when there are rows, the state view's slab when there are not. Lazy items are composed
        // during layout, so retry across a few frames rather than assuming the node is there. The
        // requester is the scaffold's, so the same node is both "first focus" and the rail's RIGHT
        // target.
        val firstCard = contentFocusRequester
        val hasRows = uiState.rows.isNotEmpty()
        val focusTarget = when {
            uiState.loading && !hasRows -> ContentFocusTarget.Loading
            !hasRows && uiState.error != null -> ContentFocusTarget.Error
            !hasRows -> ContentFocusTarget.Empty
            else -> ContentFocusTarget.Rows
        }
        LaunchedEffect(focusTarget) {
            if (focusTarget == ContentFocusTarget.Loading) return@LaunchedEffect
            repeat(5) {
                withFrameNanos { }
                if (runCatching { firstCard.requestFocus() }.isSuccess) return@LaunchedEffect
            }
        }

        // The state views hold one slab; binding the requester to the group around it makes that
        // slab the arrival target, and LEFT off it opens the rail exactly as it does off a row.
        val stateViewModifier = Modifier
            .focusRequester(firstCard)
            .focusProperties { left = railFocusRequester }
            .focusGroup()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink000),
        ) {
            // Layer 0 — art. Collects `hero` itself; see the KDoc above.
            HeroBackdrop(viewModel = viewModel)

            // Layer 1 — copy. Also collects `hero` itself, and is drawn under the rows so a
            // scrolled row passes over it instead of colliding with it.
            HeroCopy(
                viewModel = viewModel,
                onPlay = onNavigateToDetail,
                onMoreInfo = onNavigateToDetail,
            )

            // Layer 2 — the rows.
            when {
                // §6 — loading is a skeleton of the content, at the real row dimensions, so the
                // swap to real cards does not jump.
                uiState.loading && uiState.rows.isEmpty() -> Column(
                    modifier = Modifier.padding(top = HeroBlockHeight),
                    verticalArrangement = Arrangement.spacedBy(
                        (Dimens.RowSpacing - RowGlowBleed * 2).coerceAtLeast(0.dp),
                    ),
                ) {
                    SkeletonRow()
                    SkeletonRow()
                }

                uiState.rows.isEmpty() && uiState.error != null -> ErrorView(
                    message = uiState.error.orEmpty(),
                    onRetry = viewModel::retry,
                    modifier = stateViewModifier,
                    startPadding = Dimens.OverscanHorizontal,
                    topPadding = HeroBlockHeight,
                )

                // §6 — empty is composed and actionable, never a dead end.
                uiState.rows.isEmpty() -> EmptyView(
                    message = stringResource(R.string.home_empty_title),
                    detail = stringResource(R.string.empty_check_library_detail),
                    actionLabel = stringResource(R.string.action_open_settings),
                    onAction = { onNavigate(NavRailDestination.Settings) },
                    modifier = stateViewModifier,
                    startPadding = Dimens.OverscanHorizontal,
                    topPadding = HeroBlockHeight,
                )

                else -> CompositionLocalProvider(
                    LocalBringIntoViewSpec provides HomeBringIntoViewSpec,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            // Anti-trap: LEFT out of the row list opens the rail. A card inside a row
                            // resolves its own LEFT neighbour first, so this only fires at the edge.
                            .focusProperties { left = railFocusRequester },
                        // Each row already carries RowGlowBleed above and below its cards; subtract it
                        // so the *visual* rhythm is RowSpacing (§5).
                        verticalArrangement = Arrangement.spacedBy(
                            (Dimens.RowSpacing - RowGlowBleed * 2).coerceAtLeast(0.dp),
                        ),
                        contentPadding = PaddingValues(
                            // The hero owns the top of the canvas; row 0's title starts under it.
                            top = (HeroBlockHeight - RowGlowBleed).coerceAtLeast(0.dp),
                            bottom = Dimens.OverscanVertical,
                        ),
                    ) {
                        itemsIndexed(uiState.rows, key = { _, row -> row.key }) { index, row ->
                            MediaRow(
                                title = row.resolveTitle(),
                                items = row.items,
                                onItemClick = { id ->
                                    if (row.key == ROW_MY_MEDIA) {
                                        viewModel.libraryRouteFor(id)?.let { target ->
                                            onNavigateToLibrary(
                                                target.id,
                                                target.name,
                                                target.collectionType,
                                            )
                                        }
                                    } else {
                                        onNavigateToDetail(id)
                                    }
                                },
                                // Guardrail 7 — one lambda per row, never one per card, and it writes
                                // to a flow no row observes.
                                onItemFocused = { viewModel.onCardFocused(row.key, it.id) },
                                firstItemFocusRequester = if (index == 0) firstCard else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * §5.1 / §7 (4) — the billboard backdrop: full-bleed, cropped, at [HERO_BACKDROP_ALPHA] under the
 * §2.3 left wipe and bottom settle, so type always sits on near-solid black.
 *
 * Guardrail 7 — this composable collects `hero` **itself**, which is the whole reason the hero is a
 * second flow. A plain [Crossfade] and never a sliding `AnimatedContent`: art that slides in reads
 * as a carousel, and Home has no carousel.
 */
@Composable
private fun HeroBackdrop(viewModel: HomeViewModel) {
    val hero by viewModel.hero.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = hero,
            animationSpec = tween(Dur.HeroCross, easing = Ease.Decel),
            label = "homeHeroBackdrop",
        ) { current ->
            if (current?.backdropUrl != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // §5 — the stage fades up from the item's own colour, not from black.
                    val blur = rememberBlurHashPainter(current.backdropBlurHash)
                    if (blur != null) {
                        Image(
                            painter = blur,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = HERO_BLURHASH_ALPHA,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    AsyncImage(
                        model = current.backdropUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.CenterEnd,
                        alpha = HERO_BACKDROP_ALPHA,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxSize().background(Scrims.DetailWipe))
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(HeroBlockHeight)
                .background(Scrims.BottomSettle),
        )
    }
}

/**
 * Compact hero copy matching the Home hierarchy: logo (or title), a single title/metadata strip,
 * then the two actions. Long synopsis copy belongs on Detail and no longer competes with row zero.
 *
 * Guardrail 7 — like [HeroBackdrop], this collects `hero` itself.
 */
@Composable
private fun HeroCopy(
    viewModel: HomeViewModel,
    onPlay: (String) -> Unit,
    onMoreInfo: (String) -> Unit,
) {
    val hero by viewModel.hero.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(HeroBlockHeight),
    ) {
        Crossfade(
            targetState = hero,
            animationSpec = tween(Dur.HeroCross, easing = Ease.Decel),
            label = "homeHeroCopy",
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(
                    start = Dimens.OverscanHorizontal,
                    top = Dimens.OverscanVertical,
                )
                .width(Dimens.BodyMaxWidth),
        ) { current ->
            if (current != null) {
                Column {
                    HeroTitle(title = current.title, logoUrl = current.logoUrl)
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        if (current.logoUrl != null) {
                            Text(
                                text = current.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Paper,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (current.specLine.isNotBlank()) {
                            Text(
                                text = current.specLine,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Ash600,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SlabButton(
                            text = stringResource(
                                if (current.canResume) R.string.resume else R.string.play,
                            ),
                            onClick = { onPlay(current.itemId) },
                        )
                        SlabButton(
                            text = stringResource(R.string.more_info),
                            onClick = { onMoreInfo(current.itemId) },
                            primary = false,
                        )
                    }
                }
            }
        }
    }
}

/** §5.2 idiom — the item's logo art when the server has one, the title at `displayLarge` when not. */
@Composable
private fun HeroTitle(title: String, logoUrl: String?) {
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    if (logoUrl != null && !logoFailed) {
        AsyncImage(
            model = logoUrl,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            alignment = Alignment.CenterStart,
            onError = { logoFailed = true },
            modifier = Modifier
                .heightIn(max = HeroLogoMaxHeight)
                .widthIn(max = Dimens.BodyMaxWidth),
        )
    } else {
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            color = Paper,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

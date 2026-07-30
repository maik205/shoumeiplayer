package com.maik205.shoumeiplayer.ui.screens.detail

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.ui.components.ErrorView
import com.maik205.shoumeiplayer.ui.components.FocusScale
import com.maik205.shoumeiplayer.ui.components.PosterImage
import com.maik205.shoumeiplayer.ui.components.RowHeader
import com.maik205.shoumeiplayer.ui.components.SkeletonDetail
import com.maik205.shoumeiplayer.ui.components.rememberBlurHashPainter
import com.maik205.shoumeiplayer.ui.components.shoumeiFocus
import com.maik205.shoumeiplayer.ui.navigation.containerViewModel
import com.maik205.shoumeiplayer.ui.screens.library.CheckMark
import com.maik205.shoumeiplayer.ui.screens.library.TextChip
import com.maik205.shoumeiplayer.ui.theme.Alpha
import com.maik205.shoumeiplayer.ui.theme.Ash600
import com.maik205.shoumeiplayer.ui.theme.Dimens
import com.maik205.shoumeiplayer.ui.theme.Dur
import com.maik205.shoumeiplayer.ui.theme.Ease
import com.maik205.shoumeiplayer.ui.theme.Ink000
import com.maik205.shoumeiplayer.ui.theme.Ink100
import com.maik205.shoumeiplayer.ui.theme.Ink300
import com.maik205.shoumeiplayer.ui.theme.Lit
import com.maik205.shoumeiplayer.ui.theme.LocalShoumeiMotion
import com.maik205.shoumeiplayer.ui.theme.Paper
import com.maik205.shoumeiplayer.ui.theme.Scrims
import com.maik205.shoumeiplayer.ui.theme.ShoumeiType
import com.maik205.shoumeiplayer.ui.theme.Tungsten
import com.maik205.shoumeiplayer.ui.theme.focusTween
import com.maik205.shoumeiplayer.util.Ticks

// §5.2 — the canvas block: copy column at x=48 from y=96, spec block at x=684 to 912 from y=196.
private val StageHeight = 540.dp
private val CopyColumnTop = 96.dp
private val SpecBlockTop = 196.dp
private val SpecBlockWidth = 228.dp
private val BottomSettleHeight = 200.dp

/**
 * §5.2 — Detail.
 *
 * The backdrop is wiped from the left, never scrimmed from the bottom: type sits on solid black
 * and the art stays uncropped on the right. There is no poster, because the backdrop plus the
 * title is the identity, except when the item ships only a `Primary` image.
 */
@Composable
fun DetailScreen(
    itemId: String,
    onPlay: (String, Long) -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = containerViewModel { container ->
        DetailViewModel(container.libraryRepository, container.imageUrlBuilder, itemId)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)

    when {
        // §6 — loading is a skeleton in the shape of the copy column, never a centred spinner.
        // SkeletonDetail owns the x=48 overscan inset; only the copy column's y offset is ours.
        uiState.loading -> SkeletonDetail(modifier = Modifier.padding(top = CopyColumnTop))

        uiState.item == null -> ErrorView(
            message = uiState.error ?: "We could not load this title.",
            onRetry = viewModel::retry,
            modifier = Modifier.padding(start = Dimens.OverscanHorizontal, top = 220.dp),
        )

        else -> {
            val item = uiState.item!!
            val isSeries = item.type == "Series"
            val resumeTicks = viewModel.resumePositionTicks
            val playFocus = remember { FocusRequester() }
            LaunchedEffect(item.id) { runCatching { playFocus.requestFocus() } }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Ink000)
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(StageHeight),
                ) {
                    // Plane 0 — art.
                    if (uiState.backdropUrl != null) {
                        // §5 — the 1920px stage fades up from the item's own colour, not from black.
                        val stageBlur = rememberBlurHashPainter(uiState.backdropBlurHash)
                        if (stageBlur != null) {
                            Image(
                                painter = stageBlur,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                alpha = 0.85f,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        AsyncImage(
                            model = uiState.backdropUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.CenterEnd,
                            alpha = 0.85f,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (uiState.posterUrl != null) {
                        // §5.2 fallback: only a Primary image exists — 220×330 at x=660, y=105.
                        PosterImage(
                            url = uiState.posterUrl,
                            contentDescription = item.name,
                            blurHash = uiState.posterBlurHash,
                            contentAlpha = 0.85f,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .offset(x = 660.dp, y = 105.dp)
                                .size(width = 220.dp, height = 330.dp)
                                .clip(RoundedCornerShape(4.dp)),
                        )
                    }

                    // Plane 1 — scrims. DetailWipe unchanged in either case.
                    Box(modifier = Modifier.fillMaxSize().background(Scrims.DetailWipe))
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(BottomSettleHeight)
                            .background(Scrims.BottomSettle),
                    )

                    // Plane 2 — chrome.
                    CopyColumn(
                        title = item.name.orEmpty(),
                        logoUrl = uiState.logoUrl,
                        tagline = uiState.tagline,
                        // Series read as `2016-2022` or `2016-  Ended`; a movie stays a bare year.
                        metaFields = metadataFields(
                            years = yearRange(item),
                            status = runStatus(item),
                            rating = item.officialRating,
                        ),
                        runtime = item.runTimeTicks?.takeIf { it > 0 }?.let { formatRuntime(it) },
                        genres = genreLine(uiState.genres),
                        overview = item.overview,
                        resumeFraction = uiState.resumeFraction,
                        resumeTicks = resumeTicks,
                        seasons = uiState.seasons,
                        selectedSeasonId = uiState.selectedSeasonId,
                        onSelectSeason = viewModel::selectSeason,
                        onPlay = { start -> onPlay(item.id, start) },
                        playFocus = playFocus,
                        showSeasons = isSeries,
                    )

                    if (uiState.specRows.isNotEmpty()) {
                        SpecBlock(
                            rows = uiState.specRows,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = SpecBlockTop, end = Dimens.OverscanHorizontal)
                                .width(SpecBlockWidth),
                        )
                    }
                }

                if (isSeries && uiState.episodes.isNotEmpty()) {
                    EpisodeRow(
                        episodes = uiState.episodes,
                        onEpisodeClick = { id -> onPlay(id, viewModel.resumeTicksFor(id)) },
                        modifier = Modifier.padding(top = Dimens.RowSpacing, bottom = Dimens.RowSpacing),
                    )
                }

                if (uiState.cast.isNotEmpty()) {
                    CastRow(
                        cast = uiState.cast,
                        modifier = Modifier.padding(
                            top = if (isSeries && uiState.episodes.isNotEmpty()) 0.dp else Dimens.RowSpacing,
                            bottom = Dimens.RowSpacing,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * §5.2 — title 56sp over at most two lines (or the item's logo art), tagline, metadata line,
 * resume bar, overview, genres, action slabs, season chips.
 *
 * §7 (4) — the whole column arrives on one 220ms fade: a full-screen content swap should not be a
 * hard cut at 55 inches, but choreographing five blocks against each other is decoration.
 */
@Composable
private fun BoxScope.CopyColumn(
    title: String,
    logoUrl: String?,
    tagline: String?,
    metaFields: List<String>,
    runtime: String?,
    genres: String,
    overview: String?,
    resumeFraction: Float,
    resumeTicks: Long,
    seasons: List<BaseItemDto>,
    selectedSeasonId: String?,
    onSelectSeason: (String) -> Unit,
    onPlay: (Long) -> Unit,
    playFocus: FocusRequester,
    showSeasons: Boolean,
) {
    ColumnFade(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = Dimens.OverscanHorizontal, top = CopyColumnTop)
            .width(Dimens.BodyMaxWidth),
    ) {
        Column {
            TitleBlock(title = title, logoUrl = logoUrl)
            if (!tagline.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(TaglineGap))
                Text(
                    text = tagline,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Paper.copy(alpha = Alpha.TextTertiary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The tagline eats part of the title-to-metadata gap so the stage below never shifts.
            Spacer(modifier = Modifier.height(if (tagline.isNullOrBlank()) 20.dp else 12.dp))
            MetadataLine(fields = metaFields, runtime = runtime)
            Spacer(modifier = Modifier.height(18.dp))
            if (resumeFraction > 0f) {
                Box(
                    modifier = Modifier
                        .width(240.dp)
                        .height(3.dp)
                        .background(Lit.copy(alpha = Alpha.TrackInactive)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(resumeFraction)
                            .fillMaxHeight()
                            .background(Tungsten),
                    )
                }
            }
            Spacer(modifier = Modifier.height(33.dp))
            if (!overview.isNullOrBlank()) {
                Text(
                    text = overview,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Paper.copy(alpha = 0.82f),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (genres.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = genres,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Ash600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing)) {
                if (resumeTicks > 0) {
                    SlabButton(
                        label = "Resume ${Ticks.formatDuration(Ticks.toMs(resumeTicks))}",
                        primary = true,
                        onClick = { onPlay(resumeTicks) },
                        focusRequester = playFocus,
                    )
                    SlabButton(
                        label = "Play from start",
                        primary = false,
                        onClick = { onPlay(0L) },
                    )
                } else {
                    SlabButton(
                        label = "Play",
                        primary = true,
                        onClick = { onPlay(0L) },
                        focusRequester = playFocus,
                    )
                }
            }
            if (showSeasons && seasons.isNotEmpty()) {
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.focusRestorer(),
                ) {
                    seasons.forEach { season ->
                        TextChip(
                            label = season.name.orEmpty(),
                            selected = season.id == selectedSeasonId,
                            onClick = { onSelectSeason(season.id) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * §3.2 — `2019    TV-MA    2h 44m`: three or more fields keep a tab rhythm, so the line carries no
 * separator at all. The duration is the one mono value here (§3.1).
 */
@Composable
private fun MetadataLine(fields: List<String>, runtime: String?) {
    if (fields.isEmpty() && runtime == null) return
    Row(horizontalArrangement = Arrangement.spacedBy(MetadataGap)) {
        fields.forEach { field ->
            Text(
                text = field,
                style = MaterialTheme.typography.bodyMedium,
                color = Ash600,
                maxLines = 1,
            )
        }
        if (runtime != null) {
            Text(
                text = runtime,
                style = ShoumeiType.Duration,
                color = Ash600,
                maxLines = 1,
            )
        }
    }
}

/** §3.2 — the metadata columns sit a tab apart, not a dot apart. */
private val MetadataGap: Dp = 24.dp

/**
 * §5.2 — the identity line. When the item ships `Logo` art (its own or its parent's) the logo
 * *is* the title; otherwise the 56sp `displayLarge` wordmark stands in.
 *
 * A logo that 404s or decodes badly falls back to the text title too — a missing image must never
 * leave the stage titleless.
 */
@Composable
private fun TitleBlock(title: String, logoUrl: String?) {
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    if (logoUrl != null && !logoFailed) {
        Box(
            modifier = Modifier
                .heightIn(max = LogoMaxHeight)
                .widthIn(max = Dimens.BodyMaxWidth),
            contentAlignment = Alignment.CenterStart,
        ) {
            AsyncImage(
                model = logoUrl,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = Alignment.CenterStart,
                onError = { logoFailed = true },
                modifier = Modifier.fillMaxWidth().height(LogoMaxHeight),
            )
        }
    } else {
        Text(
            text = title,
            style = MaterialTheme.typography.displayLarge,
            color = Paper,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** §5.2 — logo art never grows past the two-line `displayLarge` block it replaces. */
private val LogoMaxHeight: Dp = 120.dp

/** §5.2 — the tagline sits 14dp under the title and 12dp above the spec line. */
private val TaglineGap: Dp = 14.dp

/** §7 (4) — one 220ms fade with a 16dp settle for the entire copy column. */
@Composable
private fun ColumnFade(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val progress by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = Dur.ScreenIn, easing = Ease.Decel),
        label = "detailColumn",
    )
    Box(
        modifier = modifier.graphicsLayer {
            alpha = progress
            translationY = (1f - progress) * 16.dp.toPx()
        },
    ) {
        content()
    }
}

/**
 * §5.2 — the spec block: two columns, label left at `@0.55` and value right in `Paper`, no rules
 * between rows. Durations are the only mono values (§3.1).
 */
@Composable
private fun SpecBlock(rows: List<SpecRow>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(SpecRowGap),
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = Paper.copy(alpha = Alpha.TextTertiary),
                    maxLines = 1,
                )
                Text(
                    text = row.value,
                    style = if (row.mono) {
                        ShoumeiType.Duration
                    } else {
                        MaterialTheme.typography.bodyMedium
                    },
                    color = Paper,
                    maxLines = 1,
                )
            }
        }
    }
}

/** §5.2 — 14dp row pitch, and nothing drawn between the rows. */
private val SpecRowGap: Dp = 14.dp

/**
 * §4.3 — slab buttons: primary is a `Paper` fill with a black label, 52dp tall, 28dp h-padding;
 * secondary is transparent over a 1dp `Ink300`. Focused takes a 2dp white rim and scale 1.03.
 * Labels are sentence case as authored, never uppercase, and never pills.
 */
@Composable
private fun SlabButton(
    label: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val motion = LocalShoumeiMotion.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    val scale by animateFloatAsState(
        targetValue = motion.scale(if (focused) 1.03f else 1f),
        animationSpec = focusTween(focused),
        label = "slabScale",
    )
    val rim by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = focusTween(focused),
        label = "slabRim",
    )

    var slabModifier = modifier
        .zIndex(if (focused) 1f else 0f)
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0f, 0.5f)
        }
        .height(52.dp)
        .clip(shape)
        .background(
            when {
                primary && focused -> Color.White
                primary -> Paper
                focused -> Lit.copy(alpha = 0.06f)
                else -> Color.Transparent
            },
        )
        .border(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) {
                Color.White.copy(alpha = rim)
            } else if (primary) {
                Color.Transparent
            } else {
                Ink300
            },
            shape = shape,
        )
        .onFocusChanged { focused = it.isFocused }
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    if (focusRequester != null) slabModifier = slabModifier.focusRequester(focusRequester)

    Box(modifier = slabModifier, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            color = if (primary) Ink000 else Paper,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}

/**
 * §5.2 — 320×180 thumbs with an `S1:E04` badge (no plate); title + runtime *below* the card.
 * Watched = thumb `alpha 0.55` + a 12dp white check bottom-right; focus restores alpha over 180ms.
 */
@Composable
private fun EpisodeRow(
    episodes: List<EpisodeUi>,
    onEpisodeClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var rowFocused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { rowFocused = it.hasFocus },
    ) {
        // §5 — a plain sentence-case heading: no eyebrow, no rule, no count.
        RowHeader("Episodes", active = rowFocused)
        Spacer(modifier = Modifier.height(Dimens.RowTitleGap))
        LazyRow(
            modifier = Modifier.focusRestorer(),
            // §4.1 clipping hazard: overscan + 12dp of cross-axis breathing room for the scale.
            contentPadding = PaddingValues(
                start = Dimens.OverscanHorizontal,
                end = 24.dp,
                top = 14.dp,
                bottom = 14.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
        ) {
            itemsIndexed(episodes, key = { _, e -> e.id }) { _, episode ->
                EpisodeCard(episode = episode, onClick = { onEpisodeClick(episode.id) })
            }
        }
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodeUi,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = LocalShoumeiMotion.current
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    val scale by animateFloatAsState(
        targetValue = motion.scale(if (focused) FocusScale.Wide else 1f),
        animationSpec = focusTween(focused),
        label = "episodeScale",
    )
    // §7 (1) — scale, veil and rim share one tween per focus event.
    val veil by animateFloatAsState(
        targetValue = if (focused) 0f else Alpha.VeilUnfocused,
        animationSpec = focusTween(focused),
        label = "episodeVeil",
    )
    val rim by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = focusTween(focused),
        label = "episodeRim",
    )
    // §7 (2) — a watched thumb lifts back to full alpha when you point at it.
    val artAlpha by animateFloatAsState(
        targetValue = if (episode.watched && !focused) Alpha.Watched else 1f,
        animationSpec = tween(Dur.WatchedFade),
        label = "episodeArt",
    )

    Column(
        modifier = modifier
            .width(Dimens.EpisodeCardWidth)
            .zIndex(if (focused) 1f else 0f)
            .onFocusChanged { focused = it.isFocused }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, 0.62f)
                }
                .fillMaxWidth()
                .height(Dimens.EpisodeCardHeight)
                .clip(shape)
                .background(Ink100),
        ) {
            if (episode.imageUrl != null) {
                AsyncImage(
                    model = episode.imageUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer { alpha = artAlpha },
                )
            }
            Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = veil)))
            Text(
                text = episode.badge,
                style = MaterialTheme.typography.labelSmall,
                color = Paper.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            )
            if (episode.progressFraction != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Lit.copy(alpha = Alpha.TrackInactive)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(episode.progressFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(Tungsten),
                    )
                }
            }
            if (episode.watched) {
                CheckMark(
                    dimension = 12.dp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp),
                )
            }
            Box(modifier = Modifier.matchParentSize().border(2.dp, Color.White.copy(alpha = rim), shape))
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .border(1.dp, Color.Black.copy(alpha = 0.55f * rim), shape),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(EpisodeLabelHeight)
                .padding(top = 8.dp),
        ) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.titleMedium,
                color = Paper.copy(alpha = if (focused) 1f else Alpha.TextTertiary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (episode.runtime != null) {
                Text(
                    text = episode.runtime,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash600,
                    maxLines = 1,
                )
            }
        }
    }
}

private val EpisodeLabelHeight: Dp = 56.dp

// §5.2 cast row: 120×120 portraits, fixed 44dp label block so the row never reflows.
private val CastTileSize: Dp = 120.dp
private val CastLabelHeight: Dp = 44.dp

/**
 * §5.2 — the "Cast" heading over a row of square portraits. Non-clickable until a Person screen exists:
 * the tiles are `focusable()` only, so the D-pad can walk the row and read every name without
 * offering a dead-end click.
 */
@Composable
private fun CastRow(cast: List<CastUi>, modifier: Modifier = Modifier) {
    var rowFocused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { rowFocused = it.hasFocus },
    ) {
        RowHeader("Cast", active = rowFocused)
        Spacer(modifier = Modifier.height(Dimens.RowTitleGap))
        LazyRow(
            modifier = Modifier.focusRestorer(),
            // §4.1 clipping hazard: overscan + cross-axis breathing room for the focus scale.
            contentPadding = PaddingValues(
                start = Dimens.OverscanHorizontal,
                end = 24.dp,
                top = 14.dp,
                bottom = 14.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(Dimens.ItemSpacing),
        ) {
            itemsIndexed(cast, key = { index, person -> "${person.id}#$index" }) { _, person ->
                CastTile(person = person)
            }
        }
    }
}

/**
 * §4.2 — the focus signals at the grid scale (1.04): a row of squares collides at 1.08.
 * Portrait, then name (`@0.55`→`@1.0`) and role in a fixed label block.
 */
@Composable
private fun CastTile(person: CastUi, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(4.dp)
    // §7 (1) — one focus tween drives veil and rim alongside the scale in `shoumeiFocus`.
    val veil by animateFloatAsState(
        targetValue = if (focused) 0f else Alpha.VeilUnfocused,
        animationSpec = focusTween(focused),
        label = "castVeil",
    )
    val rim by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = focusTween(focused),
        label = "castRim",
    )

    Column(
        modifier = modifier
            .width(CastTileSize)
            .onFocusChanged { focused = it.isFocused }
            .focusable(),
    ) {
        Box(
            modifier = Modifier
                .shoumeiFocus(focused = focused, scaleTo = FocusScale.GridTile, label = "castScale")
                .size(CastTileSize)
                .clip(shape),
        ) {
            PosterImage(
                url = person.imageUrl,
                contentDescription = person.name,
                aspect = 1f,
                veilAlpha = veil,
                blurHash = person.blurHash,
                modifier = Modifier.fillMaxSize(),
            )
            Box(modifier = Modifier.matchParentSize().border(2.dp, Color.White.copy(alpha = rim), shape))
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(2.dp)
                    .border(1.dp, Color.Black.copy(alpha = 0.55f * rim), shape),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(CastLabelHeight)
                .padding(top = 8.dp),
        ) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.titleSmall,
                color = Paper.copy(alpha = if (focused) 1f else Alpha.TextTertiary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (person.role != null) {
                Text(
                    text = person.role,
                    style = MaterialTheme.typography.bodySmall,
                    color = Ash600,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

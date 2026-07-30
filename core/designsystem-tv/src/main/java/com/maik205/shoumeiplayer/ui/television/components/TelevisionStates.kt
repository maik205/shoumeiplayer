package com.maik205.shoumeiplayer.ui.television.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors

@Composable
fun TelevisionLoadingState(
    modifier: Modifier = Modifier,
    label: String = "Loading…",
    shape: TelevisionLoadingShape = TelevisionLoadingShape.Rail,
) {
    val transition = rememberInfiniteTransition(label = "loading-shimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0.42f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "loading-alpha",
    )
    when (shape) {
        TelevisionLoadingShape.Detail -> DetailSkeleton(modifier, shimmer)
        TelevisionLoadingShape.Grid,
        TelevisionLoadingShape.Search,
        -> GridSkeleton(modifier, shimmer, columns = if (shape == TelevisionLoadingShape.Search) 5 else 7)
        TelevisionLoadingShape.Guide -> GuideSkeleton(modifier, shimmer)
        TelevisionLoadingShape.Home -> HomeSkeleton(modifier, shimmer)
        TelevisionLoadingShape.Rail,
        TelevisionLoadingShape.Startup,
        -> RailSkeleton(modifier, shimmer, label)
    }
}

enum class TelevisionLoadingShape { Rail, Home, Grid, Search, Detail, Guide, Startup }

@Composable
private fun RailSkeleton(modifier: Modifier, shimmer: Float, label: String) {
    Column(modifier.fillMaxWidth().widthIn(max = 550.dp)) {
        Text(label, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = TelevisionColors.PaperMuted)
        Spacer(Modifier.height(17.dp))
        SkeletonPosterRow(shimmer)
    }
}

@Composable
private fun HomeSkeleton(modifier: Modifier, shimmer: Float) {
    Column(modifier.fillMaxWidth().padding(end = 54.dp)) {
        repeat(3) { index ->
            SkeletonBlock(
                Modifier
                    .fillMaxWidth(if (index == 0) 0.24f else 0.18f)
                    .height(18.dp)
                    .graphicsLayer { alpha = shimmer },
            )
            Spacer(Modifier.height(10.dp))
            SkeletonPosterRow(shimmer)
            if (index < 2) Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun GridSkeleton(modifier: Modifier, shimmer: Float, columns: Int) {
    Column(modifier.fillMaxWidth()) {
        repeat(2) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                repeat(columns) {
                    SkeletonBlock(
                        Modifier
                            .weight(1f)
                            .aspectRatio(2f / 3f)
                            .graphicsLayer { alpha = shimmer },
                    )
                }
            }
            Spacer(Modifier.height(11.dp))
        }
    }
}

@Composable
private fun DetailSkeleton(modifier: Modifier, shimmer: Float) {
    Row(modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        SkeletonBlock(
            Modifier
                .fillMaxHeight()
                .weight(0.92f)
                .graphicsLayer { alpha = shimmer },
        )
        Column(Modifier.weight(1f).padding(top = 12.dp)) {
            SkeletonBlock(Modifier.fillMaxWidth(0.72f).height(28.dp).graphicsLayer { alpha = shimmer })
            Spacer(Modifier.height(16.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.46f).height(14.dp).graphicsLayer { alpha = shimmer })
            Spacer(Modifier.height(22.dp))
            repeat(3) {
                SkeletonBlock(Modifier.fillMaxWidth(if (it == 2) 0.68f else 0.92f).height(12.dp).graphicsLayer { alpha = shimmer })
                Spacer(Modifier.height(9.dp))
            }
        }
    }
}

@Composable
private fun GuideSkeleton(modifier: Modifier, shimmer: Float) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(5) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SkeletonBlock(Modifier.width(92.dp).height(18.dp).graphicsLayer { alpha = shimmer })
                SkeletonBlock(
                    Modifier
                        .weight(1f)
                        .height(if (row % 2 == 0) 18.dp else 30.dp)
                        .graphicsLayer { alpha = shimmer },
                )
            }
        }
    }
}

@Composable
private fun SkeletonPosterRow(shimmer: Float) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        repeat(5) {
            SkeletonBlock(
                Modifier
                    .weight(1f)
                    .aspectRatio(2f / 3f)
                    .graphicsLayer { alpha = shimmer },
            )
        }
    }
}

@Composable
private fun SkeletonBlock(modifier: Modifier) {
    Box(modifier.clip(RoundedCornerShape(4.dp)).background(TelevisionColors.ImagePlaceholder))
}

@Composable
fun TelevisionEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
) {
    TelevisionInlineMessage(title = title, message = message, modifier = modifier)
}

@Composable
fun TelevisionErrorState(
    title: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    focusRequester: FocusRequester? = null,
    requestInitialFocus: Boolean = true,
) {
    val retryFocus = focusRequester ?: remember { FocusRequester() }
    LaunchedEffect(requestInitialFocus) {
        if (requestInitialFocus) runCatching { retryFocus.requestFocus() }
    }

    Column(modifier = modifier) {
        TelevisionInlineMessage(title = title, message = message)
        Spacer(Modifier.height(14.dp))
        TelevisionFocusRevealButton(
            label = "Try again",
            icon = Icons.Default.Refresh,
            onClick = onRetry,
            focusRequester = retryFocus,
            expandedWidth = 126.dp,
        )
    }
}

@Composable
private fun TelevisionInlineMessage(
    title: String,
    message: String?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.widthIn(max = 580.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = TelevisionColors.PaperMuted,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = TelevisionColors.Paper,
        )
        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = TelevisionColors.PaperMuted,
            )
        }
    }
}

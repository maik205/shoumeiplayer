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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 550.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            color = TelevisionColors.PaperMuted,
        )
        Spacer(Modifier.height(17.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            repeat(5) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(2f / 3f)
                        .graphicsLayer { alpha = shimmer }
                        .clip(RoundedCornerShape(4.dp))
                        .background(TelevisionColors.ImagePlaceholder),
                )
            }
        }
    }
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
) {
    val retryFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { retryFocus.requestFocus() } }

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

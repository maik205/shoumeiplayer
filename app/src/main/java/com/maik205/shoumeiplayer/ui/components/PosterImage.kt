package com.maik205.shoumeiplayer.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maik205.shoumeiplayer.ui.theme.Ink100
import com.maik205.shoumeiplayer.ui.theme.PlaceholderHairline

/** §5 — `crossfade(220)`. Never a shimmer. */
private const val CROSSFADE_MS = 220

/**
 * §5 — artwork over one of two placeholders. When the item carries a [blurHash]
 * (`BaseItemDto.ImageBlurHashes`) the art fades up from its own colour; otherwise the placeholder
 * is the flat [Ink100] rect with a `1.dp #212125` inset hairline. The hairline says "deliberately
 * empty", a blurhash says "loading", so they are never drawn together. A shimmer would be a lie
 * about work in progress and is never used.
 *
 * [veilAlpha] is §4.1 (a): unfocused art sits under a black veil at `0.24`, focused art loses it.
 * The caller animates the value ([com.maik205.shoumeiplayer.ui.theme.focusTween]) so the veil fades
 * on its own tween, trailing the scale.
 *
 * [contentAlpha] is the §5.2 / §5.4 watched dim (`0.55`).
 */
@Composable
fun PosterImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    aspect: Float = 2f / 3f,
    veilAlpha: Float = 0f,
    contentAlpha: Float = 1f,
    contentScale: ContentScale = ContentScale.Crop,
    blurHash: String? = null,
) {
    val platformContext = LocalPlatformContext.current
    val blurPainter = rememberBlurHashPainter(blurHash)
    Box(
        modifier = modifier
            .aspectRatio(aspect)
            .background(Ink100),
    ) {
        if (blurPainter != null) {
            Image(
                painter = blurPainter,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            // Inset hairline — the placeholder reads as a deliberate empty frame, not a broken load.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(1.dp)
                    .border(1.dp, PlaceholderHairline),
            )
        }
        if (url != null) {
            AsyncImage(
                model = remember(url, platformContext) {
                    ImageRequest.Builder(platformContext)
                        .data(url)
                        .crossfade(CROSSFADE_MS)
                        .build()
                },
                contentDescription = contentDescription,
                modifier = Modifier.matchParentSize(),
                contentScale = contentScale,
                alpha = contentAlpha,
            )
        }
        if (veilAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = veilAlpha)),
            )
        }
    }
}

package com.maik205.shoumeiplayer.ui.television.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionColors

private const val BACKDROP_CROSSFADE_MS = 320

/**
 * Full-bleed imagery with two functional contrast layers: a leading-edge reading field and a
 * vertical chrome/shelf field. They are deliberately neutral and carry no decorative colour.
 */
@Composable
fun TelevisionBackground(
    imageUrl: String?,
    modifier: Modifier = Modifier,
    imageAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val context = LocalPlatformContext.current
    val imageRequest = remember(imageUrl, context) {
        imageUrl?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(BACKDROP_CROSSFADE_MS)
                .build()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TelevisionColors.Black),
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                alignment = imageAlignment,
                contentScale = ContentScale.Crop,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to TelevisionColors.Black.copy(alpha = 0.91f),
                        0.38f to TelevisionColors.Black.copy(alpha = 0.62f),
                        0.72f to TelevisionColors.Black.copy(alpha = 0.16f),
                        1f to TelevisionColors.Black.copy(alpha = 0.34f),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to TelevisionColors.Black.copy(alpha = 0.52f),
                        0.30f to TelevisionColors.Black.copy(alpha = 0.04f),
                        0.62f to TelevisionColors.Black.copy(alpha = 0.20f),
                        1f to TelevisionColors.Black.copy(alpha = 0.92f),
                    ),
                ),
        )

        content()
    }
}

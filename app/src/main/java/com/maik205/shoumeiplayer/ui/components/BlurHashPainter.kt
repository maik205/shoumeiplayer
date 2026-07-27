package com.maik205.shoumeiplayer.ui.components

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import com.maik205.shoumeiplayer.data.image.BlurHash

/** §5 — decode small; the upscale to the tile's real size *is* the blur. */
private const val DECODE_DIMENSION = 32

/**
 * §5 — turns a `BaseItemDto.ImageBlurHashes` entry into a [Painter] to sit under the art while
 * Coil loads it, so a poster fades up from its own colour rather than from black.
 *
 * Returns `null` for a null or malformed hash — callers fall back to the flat `Ink100` rect with
 * the `1.dp #212125` inset hairline, which is the "deliberately empty" placeholder.
 */
@Composable
fun rememberBlurHashPainter(hash: String?): Painter? = remember(hash) {
    val pixels = BlurHash.decode(hash, DECODE_DIMENSION, DECODE_DIMENSION) ?: return@remember null
    val bitmap = Bitmap.createBitmap(
        pixels,
        DECODE_DIMENSION,
        DECODE_DIMENSION,
        Bitmap.Config.ARGB_8888,
    )
    BitmapPainter(bitmap.asImageBitmap(), filterQuality = FilterQuality.Low)
}

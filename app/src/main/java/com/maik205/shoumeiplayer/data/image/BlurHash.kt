package com.maik205.shoumeiplayer.data.image

import kotlin.math.absoluteValue
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign

/**
 * Pure-Kotlin BlurHash decoder for `BaseItemDto.ImageBlurHashes`.
 *
 * Deliberately free of `android.graphics` so it unit-tests on the JVM: [decode] returns raw
 * ARGB ints, and the caller wraps them in a `Bitmap`/`ImageBitmap`. Decode small (32x32) — the
 * upscale to the poster's real size *is* the blur.
 *
 * Every malformed input returns `null`; this never throws.
 */
object BlurHash {

    private const val BASE83 =
        "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz#\$%*+,-.:;=?@[]^_{|}~"

    /**
     * Decodes [hash] into [width] x [height] ARGB pixels (row-major, opaque alpha).
     *
     * @return `null` when [hash] is null, too short, contains a non-base83 character, has a
     *   length inconsistent with its size flag, or when [width]/[height] are not positive.
     */
    fun decode(hash: String?, width: Int, height: Int, punch: Float = 1f): IntArray? {
        if (hash == null || hash.length < 6) return null
        if (width <= 0 || height <= 0) return null

        val sizeFlag = decode83(hash, 0, 1) ?: return null
        val numX = sizeFlag % 9 + 1
        val numY = sizeFlag / 9 + 1
        if (hash.length != 4 + 2 * numX * numY) return null

        val quantMax = decode83(hash, 1, 2) ?: return null
        val maxValue = (quantMax + 1) / 166.0

        val colors = Array(numX * numY) { DoubleArray(3) }
        val dc = decode83(hash, 2, 6) ?: return null
        colors[0] = decodeDc(dc)
        for (i in 1 until colors.size) {
            val value = decode83(hash, 4 + i * 2, 6 + i * 2) ?: return null
            colors[i] = decodeAc(value, maxValue * punch)
        }

        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var r = 0.0
                var g = 0.0
                var b = 0.0
                for (j in 0 until numY) {
                    for (i in 0 until numX) {
                        val basis = cos(Math.PI * x * i / width) * cos(Math.PI * y * j / height)
                        val color = colors[i + j * numX]
                        r += color[0] * basis
                        g += color[1] * basis
                        b += color[2] * basis
                    }
                }
                pixels[y * width + x] =
                    (0xFF shl 24) or (linearToSrgb(r) shl 16) or (linearToSrgb(g) shl 8) or linearToSrgb(b)
            }
        }
        return pixels
    }

    /** Decodes `hash[from until to]` as a base83 integer, or `null` on any invalid character. */
    private fun decode83(hash: String, from: Int, to: Int): Int? {
        if (from < 0 || to > hash.length || from >= to) return null
        var value = 0
        for (index in from until to) {
            val digit = BASE83.indexOf(hash[index])
            if (digit < 0) return null
            value = value * 83 + digit
        }
        return value
    }

    private fun decodeDc(value: Int): DoubleArray = doubleArrayOf(
        srgbToLinear(value shr 16 and 0xFF),
        srgbToLinear(value shr 8 and 0xFF),
        srgbToLinear(value and 0xFF),
    )

    private fun decodeAc(value: Int, maxValue: Double): DoubleArray {
        val quantR = value / (19 * 19)
        val quantG = value / 19 % 19
        val quantB = value % 19
        return doubleArrayOf(
            signPow((quantR - 9) / 9.0) * maxValue,
            signPow((quantG - 9) / 9.0) * maxValue,
            signPow((quantB - 9) / 9.0) * maxValue,
        )
    }

    private fun signPow(value: Double): Double = value.absoluteValue.pow(2.0) * value.sign

    private fun srgbToLinear(value: Int): Double {
        val v = value / 255.0
        return if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }

    private fun linearToSrgb(value: Double): Int {
        val v = value.coerceIn(0.0, 1.0)
        return if (v <= 0.0031308) {
            (v * 12.92 * 255 + 0.5).toInt()
        } else {
            ((1.055 * v.pow(1 / 2.4) - 0.055) * 255 + 0.5).toInt()
        }.coerceIn(0, 255)
    }
}

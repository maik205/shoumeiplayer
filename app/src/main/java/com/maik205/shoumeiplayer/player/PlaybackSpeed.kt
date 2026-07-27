package com.maik205.shoumeiplayer.player

import java.util.Locale

/**
 * The speed ladder from docs/osd-v3.md §5: `0.5 / 0.75 / 1 / 1.25 / 1.5 / 2`.
 *
 * Pure, so both the OSD chip and any engine can share one definition of "what speeds exist" and one
 * definition of how a rate is written (`1.5×`, never `1.50×`).
 */
object PlaybackSpeed {

    const val Normal: Float = 1.0f

    /** Ordered slowest → fastest; the panel lists them in this order. */
    val Steps: List<Float> = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    /** Engines are clamped to this range so a corrupt value can never reach libmpv. */
    val Min: Float = Steps.first()
    val Max: Float = Steps.last()

    fun clamp(speed: Float): Float = speed.coerceIn(Min, Max)

    /** The ladder step closest to [speed] — used to light the active row for an engine-set rate. */
    fun nearestStep(speed: Float): Float = Steps.minByOrNull { kotlin.math.abs(it - speed) } ?: Normal

    /** True when the rate is close enough to 1× that the OSD must not badge it (§4). */
    fun isNormal(speed: Float): Boolean = kotlin.math.abs(speed - Normal) < 0.01f

    /**
     * `1×`, `1.5×`, `0.75×` — trailing zeros dropped, because `1.50×` reads like a price.
     */
    fun label(speed: Float): String {
        val text = String.format(Locale.ROOT, "%.2f", speed)
            .trimEnd('0')
            .trimEnd('.')
        return "$text×"
    }
}

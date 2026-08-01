package com.maik205.shoumeiplayer.domain.settings

/**
 * Supported choice values independent of a particular settings screen.
 *
 * Device capability filtering can be layered over these lists without changing persistence or
 * duplicating option sets in each UI.
 */
object SettingsChoices {
    val streamingBitratesMbps: List<Int?> = listOf(null, 5, 10, 20, 40, 80, 120)
    val remoteBitratesMbps = listOf(5, 10, 20, 40, 80, 120)
    val seekIntervalsSeconds = listOf(5, 10, 15, 30, 60)
    val subtitleSizesPercent = listOf(75, 90, 100, 115, 130, 150)
    val backdropRotationSeconds = listOf(10, 20, 30, 45, 60)
    val cacheDurationsSeconds = listOf(10, 20, 30, 60, 120)
    val readAheadSeconds = listOf(5, 10, 20, 30, 60)
    val forwardCacheMiB = listOf(32, 64, 128, 256, 512)
    val backwardCacheMiB = listOf(16, 32, 64, 128, 256)
    val resumeBufferSeconds = listOf(0, 1, 2, 3, 5)
    val networkTimeoutSeconds = listOf(5, 10, 15, 30, 60)
    val screensaverTimeoutMinutes = listOf(0, 5, 10, 20, 30)
    val screensaverImageDurationSeconds = listOf(10, 20, 30, 45, 60)
    val preferredLanguages: List<String?> =
        listOf(null, "English", "Japanese", "Vietnamese", "French", "German")
}

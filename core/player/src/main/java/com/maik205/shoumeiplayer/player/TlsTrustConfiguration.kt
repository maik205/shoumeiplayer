package com.maik205.shoumeiplayer.player

import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.TlsTrustSource

data class TlsMpvOptions(
    val verify: String,
    val caFile: String?,
)

fun tlsMpvOptions(settings: ClientSettings, androidBundlePath: String?): TlsMpvOptions =
    if (!settings.verifyTlsCertificates) {
        TlsMpvOptions(verify = "no", caFile = null)
    } else {
        TlsMpvOptions(
            verify = "yes",
            caFile = when (settings.tlsTrustSource) {
                TlsTrustSource.Mpv -> null
                TlsTrustSource.AndroidSystem -> androidBundlePath?.takeIf(String::isNotBlank)
            },
        )
    }

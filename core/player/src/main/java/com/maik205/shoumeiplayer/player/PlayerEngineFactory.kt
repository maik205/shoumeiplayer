package com.maik205.shoumeiplayer.player

import android.content.Context
import com.maik205.shoumeiplayer.domain.settings.PlaybackBackend

object PlayerEngineFactory {
    fun create(context: Context, backend: PlaybackBackend = PlaybackBackend.Mpv): PlayerEngine =
        when (backend) {
            PlaybackBackend.Mpv -> MpvEngine(context.applicationContext)
            PlaybackBackend.System -> SystemPlayerEngine(context.applicationContext)
        }
}

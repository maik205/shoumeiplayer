package com.maik205.shoumeiplayer.player

import android.content.Context

object PlayerEngineFactory {
    fun create(context: Context): PlayerEngine = MpvEngine(context.applicationContext)
}

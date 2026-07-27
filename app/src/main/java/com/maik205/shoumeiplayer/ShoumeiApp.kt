package com.maik205.shoumeiplayer

import android.app.Application
import com.maik205.shoumeiplayer.di.AppContainer

class ShoumeiApp : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

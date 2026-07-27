package com.maik205.shoumeiplayer.di

import androidx.compose.runtime.staticCompositionLocalOf

val LocalAppContainer = staticCompositionLocalOf<AppContainer> { error("AppContainer not provided") }

package com.maik205.shoumeiplayer

import android.os.Bundle
import android.media.AudioManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.tv.material3.Surface
import com.maik205.shoumeiplayer.di.LocalAppContainer
import com.maik205.shoumeiplayer.ui.television.components.TelevisionBrandSplash
import com.maik205.shoumeiplayer.ui.television.navigation.TelevisionNavGraph
import com.maik205.shoumeiplayer.ui.television.theme.ShoumeiTelevisionTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            CompositionLocalProvider(
                LocalAppContainer provides (application as ShoumeiApp).container
            ) {
                ShoumeiTelevisionTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape
                    ) {
                        var appReady by remember { mutableStateOf(false) }
                        var splashVisible by remember { mutableStateOf(true) }

                        LaunchedEffect(appReady) {
                            if (appReady) {
                                delay(120)
                                splashVisible = false
                            }
                        }
                        LaunchedEffect(Unit) {
                            delay(SPLASH_MAX_VISIBLE_MILLIS)
                            splashVisible = false
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onPreviewKeyEvent { splashVisible },
                        ) {
                            TelevisionNavGraph(
                                onReady = { appReady = true },
                            )

                            AnimatedVisibility(
                                visible = splashVisible,
                                enter = EnterTransition.None,
                                exit = fadeOut(animationSpec = tween(durationMillis = 420)),
                            ) {
                                TelevisionBrandSplash()
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val SPLASH_MAX_VISIBLE_MILLIS = 1_200L
    }
}

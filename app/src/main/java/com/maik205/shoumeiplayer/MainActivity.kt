package com.maik205.shoumeiplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.maik205.shoumeiplayer.di.LocalAppContainer
import com.maik205.shoumeiplayer.ui.navigation.NavGraph
import com.maik205.shoumeiplayer.ui.theme.ShoumeiPlayerTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(
                LocalAppContainer provides (application as ShoumeiApp).container
            ) {
                ShoumeiPlayerTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape
                    ) {
                        NavGraph()
                    }
                }
            }
        }
    }
}

package com.maik205.shoumeiplayer

import android.os.Bundle
import android.media.AudioManager
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.maik205.shoumeiplayer.di.LocalAppContainer
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.remote.QrCodeCanvas
import com.maik205.shoumeiplayer.remote.QrCodeGenerator
import com.maik205.shoumeiplayer.ui.television.components.TelevisionBrandSplash
import com.maik205.shoumeiplayer.ui.television.navigation.TelevisionNavGraph
import com.maik205.shoumeiplayer.ui.television.theme.ShoumeiTelevisionTheme
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionDimensions
import com.maik205.shoumeiplayer.ui.television.theme.TelevisionTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    override fun onStart() {
        super.onStart()
        val appContainer = (application as ShoumeiApp).container
        appContainer.remoteCoordinator.registerKeyDispatcher { event ->
            this.dispatchKeyEvent(event)
        }
        appContainer.remoteCoordinator.registerBackDispatcher {
            this.onBackPressedDispatcher.onBackPressed()
        }
        appContainer.remoteServer.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        val appContainer = (application as? ShoumeiApp)?.container
        appContainer?.remoteCoordinator?.registerKeyDispatcher(null)
        appContainer?.remoteCoordinator?.registerBackDispatcher(null)
        appContainer?.remoteDiscoveryService?.unregister()
        appContainer?.remoteServer?.stop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        setContent {
            val appContainer = (application as ShoumeiApp).container
            val settings by appContainer.settingsStore.settings.collectAsState(
                initial = ClientSettings(),
            )
            CompositionLocalProvider(
                LocalAppContainer provides appContainer,
            ) {
                LaunchedEffect(Unit) {
                    appContainer.settingsStore.settings
                        .map { it.displayLanguage }
                        .distinctUntilChanged()
                        .collectLatest { language ->
                            AppLocaleManager.apply(this@MainActivity, language)
                        }
                }
                LaunchedEffect(Unit) {
                    while (appContainer.remoteServer.boundPort == 0) {
                        delay(50)
                    }
                    appContainer.remoteDiscoveryService.register(appContainer.remoteServer.boundPort)
                }
                ShoumeiTelevisionTheme(settings = settings) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = RectangleShape
                    ) {
                        var appReady by remember { mutableStateOf(false) }
                        var splashVisible by remember { mutableStateOf(true) }
                        val pairingPin by appContainer.remoteCoordinator.pairingPin.collectAsState()
                        val isCardVisible by appContainer.remoteCoordinator.isCardVisible.collectAsState()
                        val connectedClients by appContainer.remoteCoordinator.connectedClients.collectAsState()

                        LaunchedEffect(appReady) {
                            if (appReady) {
                                runCatching { reportFullyDrawn() }
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
                                .onPreviewKeyEvent { event ->
                                    if (splashVisible && !appReady) return@onPreviewKeyEvent true
                                    if (isCardVisible && event.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK) {
                                        if (event.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                                            appContainer.remoteCoordinator.hidePairingCard()
                                        }
                                        return@onPreviewKeyEvent true
                                    }
                                    false
                                },
                        ) {
                            TelevisionNavGraph(
                                onReady = { appReady = true },
                            )

                            val localIp = remember { QrCodeGenerator.getLocalIpAddress() }
                            val qrUri = remember(pairingPin, appContainer.remoteServer.boundPort) {
                                val port = if (appContainer.remoteServer.boundPort > 0) appContainer.remoteServer.boundPort else 8097
                                QrCodeGenerator.buildConnectionUri(localIp, port, pairingPin)
                            }

                            BackHandler(enabled = isCardVisible) {
                                appContainer.remoteCoordinator.hidePairingCard()
                            }

                            AnimatedVisibility(
                                visible = isCardVisible,
                                enter = fadeIn(tween(140)),
                                exit = fadeOut(tween(120)),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(
                                        top = TelevisionDimensions.NavigationHeight + 10.dp,
                                        end = TelevisionDimensions.SafeHorizontal,
                                    ),
                            ) {
                                val colors = TelevisionTheme.colors
                                val boundPort = appContainer.remoteServer.boundPort
                                val effectivePort = if (boundPort > 0) boundPort else 8097
                                val isConnected = connectedClients.isNotEmpty()
                                val cardShape = RoundedCornerShape(TelevisionDimensions.FocusRadius)

                                Box(
                                    modifier = Modifier
                                        .widthIn(min = 340.dp, max = 390.dp)
                                        .clip(cardShape)
                                        .background(colors.BlackRaised)
                                        .border(
                                            width = 1.dp,
                                            color = colors.Paper.copy(alpha = 0.08f),
                                            shape = cardShape,
                                        )
                                        .padding(18.dp),
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.CastConnected,
                                                    contentDescription = null,
                                                    tint = if (isConnected) colors.Paper else colors.PaperMuted,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Text(
                                                    text = "Companion Remote",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = colors.Paper,
                                                )
                                            }

                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(
                                                        if (isConnected) colors.Paper.copy(alpha = 0.12f)
                                                        else colors.Paper.copy(alpha = 0.06f)
                                                    )
                                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .clip(CircleShape)
                                                        .background(if (isConnected) colors.Paper else colors.PaperSoft),
                                                )
                                                Text(
                                                    text = if (isConnected) "Connected" else "Ready to Pair",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = if (isConnected) colors.Paper else colors.PaperMuted,
                                                )
                                            }
                                        }

                                        Spacer(Modifier.height(14.dp))

                                        if (isConnected) {
                                            Column(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(colors.Black.copy(alpha = 0.5f))
                                                    .border(
                                                        width = 1.dp,
                                                        color = colors.Paper.copy(alpha = 0.05f),
                                                        shape = RoundedCornerShape(4.dp),
                                                    )
                                                    .padding(10.dp),
                                            ) {
                                                connectedClients.forEachIndexed { index, client ->
                                                    if (index > 0) Spacer(Modifier.height(8.dp))
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(30.dp)
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(colors.Paper.copy(alpha = 0.06f)),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Smartphone,
                                                                contentDescription = null,
                                                                tint = colors.PaperMuted,
                                                                modifier = Modifier.size(15.dp),
                                                            )
                                                        }
                                                        Column {
                                                            Text(
                                                                text = client.deviceName,
                                                                style = MaterialTheme.typography.titleSmall,
                                                                fontWeight = FontWeight.Medium,
                                                                color = colors.Paper,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                            )
                                                            Text(
                                                                text = "${client.ipAddress} · Encrypted session",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = colors.PaperSoft,
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            Spacer(Modifier.height(14.dp))
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(if (isConnected) 84.dp else 110.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color.White)
                                                    .padding(5.dp),
                                            ) {
                                                QrCodeCanvas(
                                                    content = qrUri,
                                                    modifier = Modifier.fillMaxSize(),
                                                    darkColor = Color(0xFF08090A),
                                                    lightColor = Color.White,
                                                )
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (isConnected) "Pair Another Device" else "Pairing PIN",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.PaperSoft,
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = pairingPin.orEmpty().ifBlank { "----" },
                                                    style = if (isConnected) MaterialTheme.typography.headlineMedium
                                                           else MaterialTheme.typography.displaySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = colors.Paper,
                                                )
                                                Spacer(Modifier.height(2.dp))
                                                Text(
                                                    text = "$localIp:$effectivePort",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colors.PaperMuted,
                                                )
                                                if (!isConnected) {
                                                    Spacer(Modifier.height(4.dp))
                                                    Text(
                                                        text = "Scan QR code or enter PIN in Shoumei Remote",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = colors.PaperSoft,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = splashVisible,
                                enter = EnterTransition.None,
                                exit = fadeOut(animationSpec = tween(durationMillis = 150)),
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

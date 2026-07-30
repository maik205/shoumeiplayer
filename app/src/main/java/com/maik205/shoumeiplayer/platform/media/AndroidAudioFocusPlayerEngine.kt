package com.maik205.shoumeiplayer.platform.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.maik205.shoumeiplayer.player.PlayRequest
import com.maik205.shoumeiplayer.player.PlayerEngine
import com.maik205.shoumeiplayer.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class AndroidAudioFocusPlayerEngine(
    context: Context,
    private val delegate: PlayerEngine,
) : PlayerEngine by delegate {
    private val applicationContext = context.applicationContext
    private val audioManager = applicationContext.getSystemService(AudioManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val stateLock = Any()
    private val _state = MutableStateFlow(delegate.state.value)
    private var resumeOnFocusGain = false
    private var ownsFocus = false
    private var noisyReceiverRegistered = false

    override val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != AudioManager.ACTION_AUDIO_BECOMING_NOISY) return
            synchronized(stateLock) { resumeOnFocusGain = false }
            delegate.pause()
            abandonFocus()
        }
    }

    private var focusRequest = buildFocusRequest(audioOnly = false)

    init {
        scope.launch {
            delegate.state.collect { playerState ->
                _state.value = playerState
                updateNoisyReceiver(
                    playerState == PlayerState.Playing || playerState == PlayerState.Buffering,
                )
                if (playerState == PlayerState.Ended ||
                    playerState == PlayerState.Idle ||
                    playerState is PlayerState.Error
                ) {
                    abandonFocus()
                }
            }
        }
    }

    override fun load(item: PlayRequest) {
        synchronized(stateLock) {
            if (!ownsFocus) focusRequest = buildFocusRequest(audioOnly = !item.requiresVideoSurface)
        }
        if (requestFocus()) {
            delegate.load(item)
        } else {
            _state.value = PlayerState.Error("Another app is using audio")
        }
    }

    override fun play() {
        if (requestFocus()) {
            delegate.play()
        } else {
            _state.value = PlayerState.Error("Unable to acquire audio focus")
        }
    }

    override fun pause() {
        synchronized(stateLock) { resumeOnFocusGain = false }
        delegate.pause()
    }

    override fun stop() {
        synchronized(stateLock) { resumeOnFocusGain = false }
        delegate.stop()
        abandonFocus()
    }

    override fun release() {
        synchronized(stateLock) { resumeOnFocusGain = false }
        updateNoisyReceiver(false)
        abandonFocus()
        scope.cancel()
        delegate.release()
    }

    private fun requestFocus(): Boolean {
        synchronized(stateLock) {
            if (ownsFocus) return true
        }
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        synchronized(stateLock) { ownsFocus = granted }
        return granted
    }

    private fun buildFocusRequest(audioOnly: Boolean): AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(
                        if (audioOnly) AudioAttributes.CONTENT_TYPE_MUSIC else AudioAttributes.CONTENT_TYPE_MOVIE,
                    )
                    .build(),
            )
            .setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener(::onAudioFocusChanged, Handler(Looper.getMainLooper()))
            .build()

    private fun abandonFocus() {
        val shouldAbandon = synchronized(stateLock) {
            if (!ownsFocus) return
            ownsFocus = false
            true
        }
        if (shouldAbandon) audioManager.abandonAudioFocusRequest(focusRequest)
    }

    private fun onAudioFocusChanged(change: Int) {
        val wasPlaying = delegate.state.value == PlayerState.Playing ||
            delegate.state.value == PlayerState.Buffering
        when (audioFocusAction(change, wasPlaying, synchronized(stateLock) { resumeOnFocusGain })) {
            AudioFocusAction.Resume -> {
                synchronized(stateLock) {
                    ownsFocus = true
                    resumeOnFocusGain = false
                }
                delegate.play()
            }
            AudioFocusAction.Pause -> {
                synchronized(stateLock) { resumeOnFocusGain = wasPlaying }
                delegate.pause()
            }
            AudioFocusAction.PauseAndAbandon -> {
                synchronized(stateLock) { resumeOnFocusGain = false }
                delegate.pause()
                abandonFocus()
            }
            AudioFocusAction.Ignore -> Unit
        }
    }

    private fun updateNoisyReceiver(playing: Boolean) {
        if (playing && !noisyReceiverRegistered) {
            ContextCompat.registerReceiver(
                applicationContext,
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
            noisyReceiverRegistered = true
        } else if (!playing && noisyReceiverRegistered) {
            runCatching { applicationContext.unregisterReceiver(noisyReceiver) }
            noisyReceiverRegistered = false
        }
    }
}

internal fun audioFocusAction(
    change: Int,
    wasPlaying: Boolean,
    resumeOnFocusGain: Boolean,
): AudioFocusAction = when (change) {
    AudioManager.AUDIOFOCUS_GAIN -> if (resumeOnFocusGain) AudioFocusAction.Resume else AudioFocusAction.Ignore
    AudioManager.AUDIOFOCUS_LOSS -> AudioFocusAction.PauseAndAbandon
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,
    -> if (wasPlaying) AudioFocusAction.Pause else AudioFocusAction.Ignore
    else -> AudioFocusAction.Ignore
}

internal enum class AudioFocusAction {
    Resume,
    Pause,
    PauseAndAbandon,
    Ignore,
}

package com.maik205.shoumeiplayer.remote

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyCharacterMap
import android.view.KeyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class RemoteConnectedClient(
    val id: String,
    val deviceName: String,
    val ipAddress: String,
    val connectedAtMs: Long = System.currentTimeMillis(),
)

data class RemoteInputFocusState(
    val isFocused: Boolean = false,
    val text: String = "",
    val fieldHint: String? = null,
)

/**
 * Coordinates incoming commands from the companion app and outgoing playback state
 * to connected remote clients.
 */
class RemoteCommandCoordinator(
    private val scope: CoroutineScope,
    private val context: Context? = null,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val audioManager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    init {
        context?.let { ctx ->
            try {
                val filter = IntentFilter("android.media.VOLUME_CHANGED_ACTION")
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) {
                        val streamType = intent?.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1)
                        if (streamType == -1 || streamType == AudioManager.STREAM_MUSIC) {
                            val streamVal = intent?.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1) ?: -1
                            if (streamVal >= 0 && audioManager != null) {
                                val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
                                val pct = if (max > min) {
                                    (((streamVal - min).toFloat() / (max - min)) * 100).roundToInt().coerceIn(0, 100)
                                } else 100
                                _nowPlaying.value = _nowPlaying.value.copy(
                                    volume = pct,
                                    isMuted = isSystemMuted(),
                                )
                            } else {
                                syncSystemVolume()
                            }
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
                } else {
                    ctx.registerReceiver(receiver, filter)
                }
            } catch (_: Exception) {}
        }
    }

    @Volatile
    private var keyDispatcher: ((KeyEvent) -> Boolean)? = null

    @Volatile
    private var backDispatcher: (() -> Unit)? = null

    @Volatile
    private var playbackCommandHandler: ((RemotePlaybackAction, Long?, Long?) -> Unit)? = null

    @Volatile
    private var trackSelectHandler: ((Int, RemoteTrackType) -> Unit)? = null

    @Volatile
    private var qualitySelectHandler: ((String) -> Unit)? = null

    private val _nowPlaying = MutableStateFlow(
        RemoteNowPlayingState(
            volume = getSystemVolume(),
            isMuted = isSystemMuted(),
        )
    )
    val nowPlaying: StateFlow<RemoteNowPlayingState> = _nowPlaying.asStateFlow()

    private val _playItemRequests = MutableSharedFlow<RemoteMessage.PlayItem>(extraBufferCapacity = 16)
    val playItemRequests: SharedFlow<RemoteMessage.PlayItem> = _playItemRequests.asSharedFlow()

    private val _openItemRequests = MutableSharedFlow<RemoteMessage.OpenItem>(extraBufferCapacity = 16)
    val openItemRequests: SharedFlow<RemoteMessage.OpenItem> = _openItemRequests.asSharedFlow()

    private val _openLibraryRequests = MutableSharedFlow<RemoteMessage.OpenLibrary>(extraBufferCapacity = 16)
    val openLibraryRequests: SharedFlow<RemoteMessage.OpenLibrary> = _openLibraryRequests.asSharedFlow()

    private val _pairingPin = MutableStateFlow<String?>(null)
    val pairingPin: StateFlow<String?> = _pairingPin.asStateFlow()

    private val _isCardVisible = MutableStateFlow(false)
    val isCardVisible: StateFlow<Boolean> = _isCardVisible.asStateFlow()

    private val _connectedClients = MutableStateFlow<List<RemoteConnectedClient>>(emptyList())
    val connectedClients: StateFlow<List<RemoteConnectedClient>> = _connectedClients.asStateFlow()

    private val _inputFocusState = MutableStateFlow(RemoteInputFocusState())
    val inputFocusState: StateFlow<RemoteInputFocusState> = _inputFocusState.asStateFlow()

    @Volatile
    private var textInputSetter: ((String) -> Unit)? = null

    fun updateInputFocus(
        isFocused: Boolean,
        text: String = "",
        fieldHint: String? = null,
        onSetText: ((String) -> Unit)? = null,
    ) {
        if (isFocused) {
            textInputSetter = onSetText
            _inputFocusState.value = RemoteInputFocusState(
                isFocused = true,
                text = text,
                fieldHint = fieldHint,
            )
        } else {
            textInputSetter = null
            _inputFocusState.value = RemoteInputFocusState(isFocused = false)
        }
    }

    fun setText(text: String) {
        mainHandler.post {
            val setter = textInputSetter
            if (setter != null) {
                setter(text)
            } else {
                dispatchTextInput(text)
            }
        }
    }

    fun showPairingCard() {
        if (_pairingPin.value == null) {
            getOrCreatePairingPin()
        }
        _isCardVisible.value = true
    }

    fun hidePairingCard() {
        _isCardVisible.value = false
    }

    fun togglePairingCard() {
        if (_isCardVisible.value) {
            hidePairingCard()
        } else {
            showPairingCard()
        }
    }

    fun onClientConnected(id: String, deviceName: String, ipAddress: String) {
        val client = RemoteConnectedClient(id = id, deviceName = deviceName, ipAddress = ipAddress)
        _connectedClients.value = _connectedClients.value.filterNot { it.id == id } + client
    }

    fun onClientDisconnected(id: String) {
        _connectedClients.value = _connectedClients.value.filterNot { it.id == id }
    }

    fun getOrCreatePairingPin(): String {
        val current = _pairingPin.value
        if (current != null) return current
        val pin = (kotlin.math.abs(java.security.SecureRandom().nextInt()) % 10000).toString().padStart(4, '0')
        _pairingPin.value = pin
        return pin
    }

    fun registerKeyDispatcher(dispatcher: ((KeyEvent) -> Boolean)?) {
        this.keyDispatcher = dispatcher
    }

    fun registerBackDispatcher(dispatcher: (() -> Unit)?) {
        this.backDispatcher = dispatcher
    }

    fun registerPlaybackCommandHandler(handler: ((RemotePlaybackAction, Long?, Long?) -> Unit)?) {
        this.playbackCommandHandler = handler
    }

    fun registerTrackSelectHandler(handler: ((Int, RemoteTrackType) -> Unit)?) {
        this.trackSelectHandler = handler
    }

    fun registerQualitySelectHandler(handler: ((String) -> Unit)?) {
        this.qualitySelectHandler = handler
    }

    fun selectTrack(trackId: Int, type: RemoteTrackType) {
        trackSelectHandler?.invoke(trackId, type)
    }

    fun setQuality(quality: String) {
        qualitySelectHandler?.invoke(quality)
    }

    fun setPairingPin(pin: String?) {
        _pairingPin.value = pin
    }

    fun getSystemVolume(): Int {
        val am = audioManager ?: return 100
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) am.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
        val current = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (max <= min) return 100
        return (((current - min).toFloat() / (max - min)) * 100).roundToInt().coerceIn(0, 100)
    }

    fun isSystemMuted(): Boolean {
        val am = audioManager ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.isStreamMute(AudioManager.STREAM_MUSIC)
        } else {
            am.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
        }
    }

    fun syncSystemVolume() {
        val vol = getSystemVolume()
        val muted = isSystemMuted()
        if (_nowPlaying.value.volume != vol || _nowPlaying.value.isMuted != muted) {
            _nowPlaying.value = _nowPlaying.value.copy(volume = vol, isMuted = muted)
        }
    }

    fun adjustSystemVolume(direction: Int) {
        val am = audioManager ?: return
        mainHandler.post {
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
            mainHandler.postDelayed({ syncSystemVolume() }, 50)
        }
    }

    fun toggleSystemMute() {
        val am = audioManager ?: return
        mainHandler.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_TOGGLE_MUTE, AudioManager.FLAG_SHOW_UI)
            } else {
                val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (cur > 0) {
                    am.setStreamVolume(AudioManager.STREAM_MUSIC, 0, AudioManager.FLAG_SHOW_UI)
                } else {
                    am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                }
            }
            mainHandler.postDelayed({ syncSystemVolume() }, 50)
        }
    }

    fun setSystemVolume(percent: Int) {
        val am = audioManager ?: return
        mainHandler.post {
            val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val min = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) am.getStreamMinVolume(AudioManager.STREAM_MUSIC) else 0
            val target = (min + (percent.coerceIn(0, 100) / 100f) * (max - min)).roundToInt().coerceIn(min, max)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, target, AudioManager.FLAG_SHOW_UI)
            _nowPlaying.value = _nowPlaying.value.copy(volume = percent.coerceIn(0, 100))
            mainHandler.postDelayed({ syncSystemVolume() }, 50)
        }
    }

    fun updateNowPlaying(state: RemoteNowPlayingState) {
        _nowPlaying.value = state.copy(
            volume = getSystemVolume(),
            isMuted = isSystemMuted(),
        )
    }

    fun updatePlaybackPosition(positionMs: Long, durationMs: Long, isPlaying: Boolean) {
        _nowPlaying.value = _nowPlaying.value.copy(
            positionMs = positionMs,
            durationMs = durationMs,
            isPlaying = isPlaying,
        )
    }

    fun dispatchKey(key: RemoteKey): Boolean {
        if (key == RemoteKey.BACK) {
            if (_isCardVisible.value) {
                _isCardVisible.value = false
                return true
            }
            val back = backDispatcher
            if (back != null) {
                mainHandler.post { back() }
                return true
            }
        }

        when (key) {
            RemoteKey.VOLUME_UP -> {
                adjustSystemVolume(AudioManager.ADJUST_RAISE)
                return true
            }
            RemoteKey.VOLUME_DOWN -> {
                adjustSystemVolume(AudioManager.ADJUST_LOWER)
                return true
            }
            RemoteKey.VOLUME_MUTE -> {
                toggleSystemMute()
                return true
            }
            else -> Unit
        }

        val keyCode = when (key) {
            RemoteKey.UP -> KeyEvent.KEYCODE_DPAD_UP
            RemoteKey.DOWN -> KeyEvent.KEYCODE_DPAD_DOWN
            RemoteKey.LEFT -> KeyEvent.KEYCODE_DPAD_LEFT
            RemoteKey.RIGHT -> KeyEvent.KEYCODE_DPAD_RIGHT
            RemoteKey.SELECT -> KeyEvent.KEYCODE_DPAD_CENTER
            RemoteKey.BACK -> KeyEvent.KEYCODE_BACK
            RemoteKey.HOME -> KeyEvent.KEYCODE_HOME
            RemoteKey.VOLUME_UP -> KeyEvent.KEYCODE_VOLUME_UP
            RemoteKey.VOLUME_DOWN -> KeyEvent.KEYCODE_VOLUME_DOWN
            RemoteKey.VOLUME_MUTE -> KeyEvent.KEYCODE_VOLUME_MUTE
            RemoteKey.PLAY_PAUSE -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            RemoteKey.FAST_FORWARD -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            RemoteKey.REWIND -> KeyEvent.KEYCODE_MEDIA_REWIND
            RemoteKey.MENU -> KeyEvent.KEYCODE_MENU
            RemoteKey.BACKSPACE -> KeyEvent.KEYCODE_DEL
            RemoteKey.ENTER -> KeyEvent.KEYCODE_ENTER
        }

        return postKeyEvent(keyCode)
    }

    fun dispatchTextInput(text: String) {
        mainHandler.post {
            val dispatcher = keyDispatcher ?: return@post
            val charMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
            val events = charMap.getEvents(text.toCharArray())
            if (events != null) {
                for (event in events) {
                    dispatcher(event)
                }
            }
        }
    }

    fun handlePlaybackCommand(action: RemotePlaybackAction, positionMs: Long? = null, deltaMs: Long? = null) {
        playbackCommandHandler?.invoke(action, positionMs, deltaMs)
    }

    fun requestPlayItem(request: RemoteMessage.PlayItem) {
        scope.launch {
            _playItemRequests.emit(request)
        }
    }

    fun requestOpenItem(request: RemoteMessage.OpenItem) {
        scope.launch {
            _openItemRequests.emit(request)
        }
    }

    fun requestOpenLibrary(request: RemoteMessage.OpenLibrary) {
        scope.launch {
            _openLibraryRequests.emit(request)
        }
    }

    private fun postKeyEvent(keyCode: Int): Boolean {
        mainHandler.post {
            val down = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val up = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            val dispatcher = keyDispatcher
            if (dispatcher != null) {
                dispatcher(down)
                dispatcher(up)
            }
        }
        return true
    }
}

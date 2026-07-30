package com.maik205.shoumeiplayer.platform.media

import android.os.Build
import android.view.Surface
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.RefreshRateSwitching
import com.maik205.shoumeiplayer.player.PlayerEngine
import com.maik205.shoumeiplayer.player.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class AndroidFrameRatePlayerEngine(
    private val delegate: PlayerEngine,
) : PlayerEngine by delegate {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var surface: Surface? = null
    private var settings = ClientSettings()

    init {
        scope.launch {
            delegate.videoFps.collect { applyFrameRate() }
        }
        scope.launch {
            delegate.state.collect { applyFrameRate() }
        }
    }

    override fun configure(settings: ClientSettings) {
        this.settings = settings
        delegate.configure(settings)
        applyFrameRate()
    }

    override fun setSurface(surface: Surface?) {
        this.surface = surface
        delegate.setSurface(surface)
        applyFrameRate()
    }

    override fun release() {
        clearFrameRate()
        scope.cancel()
        delegate.release()
    }

    private fun applyFrameRate() {
        val target = resolveFrameRateRequest(
            mode = settings.refreshRateSwitching,
            fps = delegate.videoFps.value,
            playbackActive = delegate.state.value == PlayerState.Playing ||
                delegate.state.value == PlayerState.Buffering,
        ) ?: run {
            clearFrameRate()
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            surface?.setFrameRate(target.fps, target.compatibility, target.strategy)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            surface?.setFrameRate(target.fps, target.compatibility)
        }
    }

    private fun clearFrameRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            surface?.setFrameRate(0f, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT)
        }
    }
}

internal data class FrameRateRequest(
    val fps: Float,
    val compatibility: Int,
    val strategy: Int,
)

internal fun resolveFrameRateRequest(
    mode: RefreshRateSwitching,
    fps: Double?,
    playbackActive: Boolean,
): FrameRateRequest? {
    if (!playbackActive || fps == null || !fps.isFinite() || fps <= 0.0) return null
    return when (mode) {
        RefreshRateSwitching.Disabled -> null
        RefreshRateSwitching.MatchVideo -> FrameRateRequest(
            fps = fps.toFloat(),
            compatibility = Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
            strategy = Surface.CHANGE_FRAME_RATE_ONLY_IF_SEAMLESS,
        )
        RefreshRateSwitching.Always -> FrameRateRequest(
            fps = fps.toFloat(),
            compatibility = Surface.FRAME_RATE_COMPATIBILITY_FIXED_SOURCE,
            strategy = Surface.CHANGE_FRAME_RATE_ALWAYS,
        )
    }
}

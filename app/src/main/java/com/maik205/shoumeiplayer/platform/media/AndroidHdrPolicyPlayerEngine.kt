package com.maik205.shoumeiplayer.platform.media

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.HdrMode
import com.maik205.shoumeiplayer.player.PlayerEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class AndroidHdrPolicyPlayerEngine(
    context: Context,
    private val delegate: PlayerEngine,
) : PlayerEngine by delegate {
    private val displayManager = context.applicationContext.getSystemService(DisplayManager::class.java)
    private val _effectiveMode = MutableStateFlow(HdrMode.Automatic)
    private var settings = ClientSettings()

    val effectiveMode: StateFlow<HdrMode> = _effectiveMode.asStateFlow()

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = reevaluate()
        override fun onDisplayRemoved(displayId: Int) = reevaluate()
        override fun onDisplayChanged(displayId: Int) = reevaluate()
    }

    init {
        displayManager?.registerDisplayListener(displayListener, null)
        reevaluate()
    }

    override fun configure(settings: ClientSettings) {
        this.settings = settings
        applySettings()
    }

    override fun release() {
        displayManager?.unregisterDisplayListener(displayListener)
        delegate.release()
    }

    private fun reevaluate() = applySettings()

    private fun applySettings() {
        val effective = effectiveHdrMode(settings.hdrMode, displaySupportsHdr())
        _effectiveMode.value = effective
        delegate.configure(settings.copy(hdrMode = effective))
    }

    private fun displaySupportsHdr(): Boolean =
        displayManager?.getDisplay(Display.DEFAULT_DISPLAY)?.hdrCapabilities?.supportedHdrTypes
            ?.isNotEmpty() == true
}

internal fun effectiveHdrMode(userMode: HdrMode, displaySupportsHdr: Boolean): HdrMode = when {
    !displaySupportsHdr && userMode in setOf(HdrMode.Automatic, HdrMode.Passthrough) -> HdrMode.ForceSdr
    else -> userMode
}

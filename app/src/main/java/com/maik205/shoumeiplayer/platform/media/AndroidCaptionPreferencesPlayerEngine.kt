package com.maik205.shoumeiplayer.platform.media

import android.content.Context
import android.view.accessibility.CaptioningManager
import com.maik205.shoumeiplayer.player.PlayerEngine
import com.maik205.shoumeiplayer.player.SystemCaptionStyle

internal class AndroidCaptionPreferencesPlayerEngine(
    context: Context,
    private val delegate: PlayerEngine,
) : PlayerEngine by delegate {
    private val captioningManager = context.applicationContext
        .getSystemService(Context.CAPTIONING_SERVICE) as CaptioningManager

    private val listener = object : CaptioningManager.CaptioningChangeListener() {
        override fun onEnabledChanged(enabled: Boolean) = applyStyle()
        override fun onUserStyleChanged(userStyle: CaptioningManager.CaptionStyle) = applyStyle()
        override fun onFontScaleChanged(fontScale: Float) = applyStyle()
        override fun onLocaleChanged(locale: java.util.Locale?) = applyStyle()
    }

    init {
        captioningManager.addCaptioningChangeListener(listener)
        applyStyle()
    }

    override fun release() {
        captioningManager.removeCaptioningChangeListener(listener)
        delegate.release()
    }

    private fun applyStyle() {
        val style = captioningManager.userStyle
        delegate.setSystemCaptionStyle(
            SystemCaptionStyle(
                enabled = captioningManager.isEnabled,
                localeTag = captioningManager.locale?.toLanguageTag(),
                fontScale = captioningManager.fontScale,
                foregroundColor = style.foregroundColor,
                backgroundColor = style.backgroundColor,
                edgeType = style.edgeType,
                edgeColor = style.edgeColor,
                typefaceName = style.typeface?.family,
            ),
        )
    }
}

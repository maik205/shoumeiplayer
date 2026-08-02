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

    /**
     * Forwards the current system caption style on every relevant change, always including
     * [CaptioningManager.isEnabled] in [SystemCaptionStyle.enabled] -- the engine (see
     * `MpvEngine.setSystemCaptionStyle` and `resolveSubtitleOptions`) is the one that gates on it,
     * restoring the in-app subtitle settings whenever this reports `enabled = false` instead of
     * leaving whatever system style values were last applied stuck in place.
     *
     * [SystemCaptionStyle.localeTag] is null unless the user explicitly picked a caption language
     * in Android's accessibility settings, which is the usual case; the engine then falls back to
     * the account's subtitle language rather than leaving mpv's `slang` unset.
     *
     * `typefaceName` stays null: [CaptioningManager.CaptionStyle.getTypeface] hands back a loaded
     * `Typeface`, not the family name libass needs, so there is nothing honest to pass on.
     */
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
                typefaceName = null,
            ),
        )
    }
}

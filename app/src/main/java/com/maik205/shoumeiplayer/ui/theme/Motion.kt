package com.maik205.shoumeiplayer.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * §7 - **four motion systems, nothing else.** Anything not listed here does not animate.
 *
 * 1. Focus feedback, so a viewer ten feet away knows where the D-pad is.
 * 2. State transitions, which render a change the user just caused or a position really moving.
 * 3. Indeterminate loading, present only while the app waits on the network or the decoder.
 * 4. Content transitions, so a full-screen swap is not a hard cut at 55 inches.
 *
 * No springs, no overshoot: a light does not bounce, and a card still moving when the next D-pad
 * press lands reads as lag. Nothing loops except the buffering hairline and indeterminate
 * progress; nothing exceeds 420ms.
 */
object Ease {
    /** Arriving / lights up. */
    val Decel = CubicBezierEasing(0.05f, 0.70f, 0.10f, 1.00f)

    /** Leaving / lights out. */
    val Accel = CubicBezierEasing(0.30f, 0.00f, 0.80f, 0.15f)

    /** Scroll. */
    val Standard = CubicBezierEasing(0.20f, 0.00f, 0.00f, 1.00f)
}

/** §7 - every duration in the app, in milliseconds, tagged with the system that justifies it. */
object Dur {
    // 1 - focus feedback. Scale, veil and rim share these; v1's four staggered tweens made the
    // answer to "where is my D-pad?" arrive late.
    const val FocusIn = 140
    const val FocusOut = 100

    // 2 - state transitions.

    /** OSD in: chrome the user just summoned. */
    const val OsdIn = 160

    /** OSD out, slower than in so chrome leaves quietly. */
    const val OsdOut = 240

    /** Idle time before the OSD hides itself. */
    const val OsdAutoHide = 5000

    /** Track panel slide, tying the panel to the edge it came from. */
    const val PanelIn = 220

    /** Progress and seek fill; linear at the call site, because eased progress reads as a lie. */
    const val ProgressValue = 240

    /** Seek repeat cadence while a direction key is held. */
    const val ScrubTick = 90

    /** Play / pause glyph crossfade, confirming the key press. */
    const val GlyphFade = 120

    /** Watched artwork restoring to full alpha as focus lands on it. */
    const val WatchedFade = 180

    /** Sign-in failure shake: the field says no. */
    const val Shake = 160

    // 3 - indeterminate loading.

    /** Buffering hairline pulse period. */
    const val BufferPulse = 900

    /** Delay before the buffering hairline appears, so brief stalls do not flash. */
    const val BufferDelay = 400

    // 4 - content transitions.

    /** Route enter fade. */
    const val ScreenIn = 220

    /** Route exit fade, faster so Back feels immediate. */
    const val ScreenOut = 180

    /** Home ambient crossfade as focus moves between cards. */
    const val AmbientCross = 400

    /** Focus settle before the ambient layer commits to a new backdrop. */
    const val AmbientDebounce = 250

    /** Row snap: the focused card lands at the row's leading edge. */
    const val RowScroll = 320

    /** Grid snap. */
    const val GridScroll = 260

    /** Home hero: focus settle before the hero commits to a new item (guardrail 7, >=300). */
    const val HeroDebounce = 320

    /** Home hero backdrop/copy crossfade as the hero item changes. */
    const val HeroCross = 400

    /** Nav rail collapsed/expanded width animation. */
    const val RailExpand = 160
}

/**
 * §7 - **the one focus tween.** Scale, veil and rim all run on this single spec per focus event:
 * the only job is telling a 10-foot viewer where the D-pad is, and v1's four staggered tweens made
 * that answer arrive late.
 */
fun focusTween(focused: Boolean): TweenSpec<Float> =
    tween(
        durationMillis = if (focused) Dur.FocusIn else Dur.FocusOut,
        easing = if (focused) Ease.Decel else Ease.Accel,
    )

@Deprecated("v2 - one tween per focus event.", ReplaceWith("focusTween(focused)"))
fun focusScaleTween(focused: Boolean): TweenSpec<Float> = focusTween(focused)

@Deprecated("v2 - the veil rides the focus tween.", ReplaceWith("focusTween(focused)"))
fun veilTween(focused: Boolean): TweenSpec<Float> = focusTween(focused)

@Deprecated("v2 - the rim rides the focus tween.", ReplaceWith("focusTween(focused)"))
fun borderTween(focused: Boolean): TweenSpec<Float> = focusTween(focused)

/**
 * §7 - degradation switch for weak SoCs. Scale is gated behind this; the 2dp white rim plus the
 * inner dark hairline must still satisfy "focus is always visible" with it off.
 */
@Immutable
data class ShoumeiMotion(
    val focusScaleEnabled: Boolean = true,
) {
    /** The effective focus scale for a card: 1f when scaling is gated off. */
    fun scale(target: Float): Float = if (focusScaleEnabled) target else 1f
}

val LocalShoumeiMotion = staticCompositionLocalOf { ShoumeiMotion() }

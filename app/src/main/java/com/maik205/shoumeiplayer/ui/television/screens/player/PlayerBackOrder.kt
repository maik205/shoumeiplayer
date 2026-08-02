package com.maik205.shoumeiplayer.ui.television.screens.player

/** The dismissable layers the player can put over playback. At most one may be open. */
internal enum class PlayerLayer { Panel, Queue, Lyrics, WhileWatching }

/**
 * Which dismissable layers are open.
 *
 * These four used to be independent booleans that each opener set on its own, and the rules for
 * closing the others were spelled out differently at every call site -- so a drawer opened over an
 * already-open queue left both claiming the same keys and the same focus (PLAYER-002). Making the
 * combination a single value means "exactly one" is expressed once and can be checked.
 */
internal data class PlayerLayers(
    val panel: Boolean = false,
    val queue: Boolean = false,
    val lyrics: Boolean = false,
    val whileWatching: Boolean = false,
) {
    /** True when nothing is over playback, so the OSD owns the remote. */
    val none: Boolean get() = !panel && !queue && !lyrics && !whileWatching

    companion object {
        val None = PlayerLayers()

        /** The only valid state after opening [layer]: it is up, and nothing else is. */
        fun opening(layer: PlayerLayer): PlayerLayers = PlayerLayers(
            panel = layer == PlayerLayer.Panel,
            queue = layer == PlayerLayer.Queue,
            lyrics = layer == PlayerLayer.Lyrics,
            whileWatching = layer == PlayerLayer.WhileWatching,
        )
    }
}

/** What Back should do, given everything the player currently has on screen. */
internal enum class PlayerBackAction {
    AnswerResumePrompt,
    ClosePanel,
    CloseQueue,
    CloseLyrics,
    ClosePostPlayBrowser,
    CloseWhileWatching,
    ExitPlayer,
    HideOsd,
    HideMiniSeek,
    ConfirmExit,
}

/**
 * Decides which layer Back dismisses.
 *
 * The player stacks a lot of things on top of playback -- selection drawers, Extras, the While
 * Watching shelf, the audio queue, lyrics, the post-play episode browser, and three full-screen
 * prompts -- and Back has to peel exactly one of them off at a time. This used to be a `when`
 * inside the BackHandler that consulted playback state first, so an open drawer or episode browser
 * was skipped and Back quit the player out from under it.
 *
 * The order below is "topmost visible thing wins", with two deliberate exceptions:
 *
 * The resume prompt sits above everything. Its focus trap swallows every key, so Back has to mean
 * what its Resume button means (§91) rather than falling through to a layer the viewer cannot
 * currently see or reach.
 *
 * The terminal states -- Still Watching, a playback error, and a finished item -- sit below the
 * dismissable layers but above the OSD, because once one of those is showing there is nothing left
 * to go back to inside the player.
 *
 * Kept separate from the composable so the ordering is testable without a device: it is the part
 * that is easy to get wrong and impossible to notice from reading a 1300-line screen.
 */
internal fun playerBackAction(
    resumePromptVisible: Boolean,
    panelOpen: Boolean,
    queueVisible: Boolean,
    lyricsVisible: Boolean,
    postPlayBrowsing: Boolean,
    whileWatchingVisible: Boolean,
    stillWatching: Boolean,
    hasPlaybackError: Boolean,
    playbackEnded: Boolean,
    osdVisible: Boolean,
    miniSeekVisible: Boolean,
    audioOnly: Boolean,
): PlayerBackAction = when {
    resumePromptVisible -> PlayerBackAction.AnswerResumePrompt
    panelOpen -> PlayerBackAction.ClosePanel
    queueVisible -> PlayerBackAction.CloseQueue
    lyricsVisible -> PlayerBackAction.CloseLyrics
    postPlayBrowsing -> PlayerBackAction.ClosePostPlayBrowser
    !audioOnly && whileWatchingVisible -> PlayerBackAction.CloseWhileWatching
    stillWatching -> PlayerBackAction.ExitPlayer
    hasPlaybackError -> PlayerBackAction.ExitPlayer
    playbackEnded -> PlayerBackAction.ExitPlayer
    !audioOnly && osdVisible -> PlayerBackAction.HideOsd
    !audioOnly && miniSeekVisible -> PlayerBackAction.HideMiniSeek
    // Leaving playback is the same decision whether it is asked for here or with the on-screen
    // Exit control, so it takes the same confirmation.
    else -> PlayerBackAction.ConfirmExit
}

package com.maik205.shoumeiplayer.ui.television.screens.screensaver

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.session.SettingsStore
import com.maik205.shoumeiplayer.domain.repository.MediaCatalog
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.domain.settings.ClientSettings
import com.maik205.shoumeiplayer.domain.settings.ScreensaverContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** One frame of the screensaver: the artwork plus the caption drawn over it. */
@Immutable
data class ScreensaverSlide(
    val imageUrl: String,
    val title: String,
    val subtitle: String?,
)

@Immutable
data class ScreensaverUiState(
    val visible: Boolean = false,
    val slide: ScreensaverSlide? = null,
    /**
     * Bumped on every slide. `ScreensaverHost`'s dwell timer keys on it, so the timer restarts per
     * slide even when two consecutive slides happen to be equal.
     */
    val slideSerial: Int = 0,
    val timeoutMinutes: Int = ClientSettings().screensaverTimeoutMinutes,
    val imageDurationMillis: Long =
        screensaverImageDurationMillis(ClientSettings().screensaverImageDurationSeconds),
    val showClock: Boolean = ClientSettings().screensaverClock,
)

/**
 * Owns the screensaver's content pool and the six settings that shape it (#87).
 *
 * The pool is built lazily -- the first time the screensaver actually arms -- and then kept, because
 * the alternative is a `/Items` round trip against every selected library each time the viewer walks
 * away from the couch. It is rebuilt when a setting that changes *what may be shown* changes;
 * [ClientSettings] fields unrelated to the screensaver leave the loaded pool alone.
 *
 * [random] is injectable purely so the shuffle order is deterministic under test.
 */
class ScreensaverViewModel(
    private val catalog: MediaCatalog,
    private val settingsStore: SettingsStore,
    private val random: Random = Random.Default,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreensaverUiState())
    val state: StateFlow<ScreensaverUiState> = _state.asStateFlow()

    /** The inputs that decide the pool's membership and order; a change to any invalidates it. */
    private data class PoolKey(
        val content: ScreensaverContent,
        val shuffle: Boolean,
        val avoidRepeats: Boolean,
    )

    private var settings: ClientSettings = ClientSettings()
    private var poolKey: PoolKey? = null
    private var rotation: ScreensaverRotation? = null
    private var startJob: Job? = null

    init {
        viewModelScope.launch {
            settingsStore.settings.collect { latest ->
                settings = latest
                val key = PoolKey(
                    content = latest.screensaverContent,
                    shuffle = latest.screensaverShuffle,
                    avoidRepeats = latest.screensaverAvoidRepeats,
                )
                if (poolKey != null && poolKey != key) rotation = null
                poolKey = key
                _state.update {
                    it.copy(
                        timeoutMinutes = latest.screensaverTimeoutMinutes,
                        imageDurationMillis =
                            screensaverImageDurationMillis(latest.screensaverImageDurationSeconds),
                        showClock = latest.screensaverClock,
                    )
                }
            }
        }
    }

    /**
     * Arms the first slide. A no-op when already showing, and -- deliberately -- also a no-op when
     * there is nothing to show: an empty or unreachable library is a reason to leave the viewer's
     * screen alone, not to black it out.
     */
    fun start() {
        if (_state.value.visible || startJob?.isActive == true) return
        startJob = viewModelScope.launch {
            val loaded = rotation ?: buildRotation() ?: return@launch
            rotation = loaded
            val slide = loaded.nextSlide() ?: return@launch
            _state.update { it.copy(visible = true, slide = slide, slideSerial = it.slideSerial + 1) }
        }
    }

    /** Moves to the next slide once the current one has had its configured dwell time. */
    fun advance() {
        if (!_state.value.visible) return
        val slide = rotation?.nextSlide() ?: return
        _state.update { it.copy(slide = slide, slideSerial = it.slideSerial + 1) }
    }

    /**
     * Hands the screen back. The rotation is kept so the next idle period resumes mid-cycle rather
     * than restarting -- which is what "avoid repeats" means to somebody who walks past the TV
     * twice in an evening.
     */
    fun dismiss() {
        startJob?.cancel()
        if (!_state.value.visible) return
        _state.update { it.copy(visible = false) }
    }

    private fun ScreensaverRotation.nextSlide(): ScreensaverSlide? {
        val item = next() ?: return null
        val url = screensaverArtworkUrl(item) ?: return null
        return ScreensaverSlide(imageUrl = url, title = item.title, subtitle = item.subtitle)
    }

    /**
     * Builds the pool from the selected content source, via the ordinary [MediaCatalog] -- so the
     * artwork URLs are the ones `ImageUrlBuilder` already produced for browse, and Coil serves them
     * out of the same artwork cache instead of a second image path.
     */
    private suspend fun buildRotation(): ScreensaverRotation? {
        val libraries = when (val result = catalog.libraries()) {
            is ApiResult.Failure -> return null
            is ApiResult.Success -> screensaverLibraries(result.data, settings.screensaverContent)
        }
        if (libraries.isEmpty()) return null

        val items = libraries.flatMap { library ->
            try {
                when (val page = catalog.latest(library.id, SCREENSAVER_ITEMS_PER_LIBRARY)) {
                    is ApiResult.Failure -> emptyList()
                    is ApiResult.Success -> page.data
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // One unreachable library must not cost the viewer the other three.
                emptyList()
            }
        }.filter { screensaverArtworkUrl(it) != null }
        if (items.isEmpty()) return null

        return ScreensaverRotation(
            items = items,
            shuffle = settings.screensaverShuffle,
            avoidRepeats = settings.screensaverAvoidRepeats,
            random = random,
        )
    }
}

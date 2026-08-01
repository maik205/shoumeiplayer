package com.maik205.shoumeiplayer.ui.television.screens.live

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.R
import com.maik205.shoumeiplayer.domain.result.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.domain.model.MediaItem as MediaItemUi
import com.maik205.shoumeiplayer.ui.television.model.toTelevisionUi
import com.maik205.shoumeiplayer.ui.i18n.UiText
import com.maik205.shoumeiplayer.ui.i18n.toUiText
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Immutable
data class LiveProgramUi(
    val item: MediaItemUi,
    val channelId: String,
    val start: Instant,
    val end: Instant,
    val startLabel: String,
    val endLabel: String,
)

@Immutable
data class LiveChannelUi(
    val item: MediaItemUi,
    val number: String?,
    val programs: List<LiveProgramUi>,
)

@Immutable
data class TelevisionLiveState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val channels: List<LiveChannelUi> = emptyList(),
    val focused: LiveProgramUi? = null,
    val windowStart: Instant = Instant.now(),
    val windowEnd: Instant = Instant.now().plusSeconds(6 * 60 * 60),
    val error: UiText? = null,
    val programError: UiText? = null,
)

class TelevisionLiveViewModel(
    private val repository: LibraryRepository,
    private val images: ImageUrlBuilder,
) : ViewModel() {
    private val _state = MutableStateFlow(TelevisionLiveState())
    val state: StateFlow<TelevisionLiveState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val hasGuide = _state.value.channels.isNotEmpty()
                _state.update {
                    it.copy(
                        loading = !hasGuide,
                        refreshing = hasGuide,
                        error = null,
                        programError = null,
                    )
                }
                val now = OffsetDateTime.now()
                val windowStart = now.minusMinutes(30).withSecond(0).withNano(0)
                val windowEnd = windowStart.plusHours(6)
                val result = coroutineScope {
                    val channels = async { fetch { repository.liveTvChannels(limit = 160) } }
                    val programs = async {
                        fetch {
                            repository.liveTvPrograms(
                                minStartDate = windowStart.toInstant().toString(),
                                maxStartDate = windowEnd.toInstant().toString(),
                                limit = 1_000,
                            )
                        }
                    }
                    channels.await() to programs.await()
                }
            val channelFetch = result.first
            val channelResult = channelFetch.getOrNull()
            if (channelResult == null) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = UiText.Resource(R.string.tv_live_load_failed),
                    )
                }
                return@launch
            }
            if (channelResult is ApiResult.Failure) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = channelResult.error.toUiText(),
                    )
                }
                return@launch
            }

            val channelItems = (channelResult as ApiResult.Success).data.items
            val previousChannels = _state.value.channels.associateBy { it.item.id }
            val programFetch = result.second
            val programResult = programFetch.getOrNull()
            val programError = when (programResult) {
                null -> UiText.Resource(R.string.tv_live_guide_failed)
                is ApiResult.Failure -> programResult.error.toUiText()
                is ApiResult.Success -> null
            }
            val programs = (programResult as? ApiResult.Success)?.data?.items
                ?.mapNotNull(::programUi)
            val grouped = programs?.groupBy(LiveProgramUi::channelId).orEmpty()
            val channels = channelItems.map { channel ->
                val scheduled = if (programs == null) {
                    previousChannels[channel.id]?.programs.orEmpty().ifEmpty {
                        listOfNotNull(channel.currentProgram?.let(::programUi))
                    }
                } else {
                    grouped[channel.id].orEmpty().ifEmpty {
                        listOfNotNull(channel.currentProgram?.let(::programUi))
                    }
                }
                LiveChannelUi(
                    item = channel.toTelevisionUi(images),
                    number = channel.channelNumber,
                    programs = scheduled.sortedBy(LiveProgramUi::start),
                )
            }
            val previousFocusedId = _state.value.focused?.item?.id
            val nextFocused = channels.asSequence()
                .flatMap { it.programs.asSequence() }
                .firstOrNull { it.item.id == previousFocusedId }
            val nextNow = Instant.now()
            val focused = nextFocused
                ?: channels.asSequence()
                    .flatMap { it.programs.asSequence() }
                    .firstOrNull { nextNow >= it.start && nextNow < it.end }
                ?: channels.firstNotNullOfOrNull { it.programs.firstOrNull() }
                _state.value = _state.value.copy(
                    loading = false,
                    refreshing = false,
                    channels = channels,
                    focused = focused,
                    windowStart = windowStart.toInstant(),
                    windowEnd = windowEnd.toInstant(),
                    error = null,
                    programError = programError,
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                _state.update {
                    it.copy(
                        loading = false,
                        refreshing = false,
                        error = UiText.Resource(R.string.tv_live_load_failed),
                    )
                }
            }
        }
    }

    fun focus(program: LiveProgramUi?) {
        _state.update { it.copy(focused = program) }
    }

    private fun programUi(item: BaseItemDto): LiveProgramUi? {
        val channelId = item.channelId ?: return null
        val start = item.startDate?.let(::parseInstant) ?: return null
        val end = item.endDate?.let(::parseInstant)
            ?: item.runTimeTicks?.let { start.plusMillis(it / 10_000L) }
            ?: return null
        return LiveProgramUi(
            item = item.toTelevisionUi(images),
            channelId = channelId,
            start = start,
            end = end,
            startLabel = clock(start),
            endLabel = clock(end),
        )
    }

    private suspend fun <T> fetch(request: suspend () -> ApiResult<T>): Result<ApiResult<T>> = try {
        Result.success(request())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Throwable) {
        Result.failure(error)
    }

    private fun parseInstant(value: String): Instant? = runCatching {
        OffsetDateTime.parse(value).toInstant()
    }.recoverCatching {
        Instant.parse(value)
    }.getOrNull()

    private fun clock(value: Instant): String = DateTimeFormatter
        .ofLocalizedTime(FormatStyle.SHORT)
        .withLocale(Locale.getDefault())
        .withZone(ZoneId.systemDefault())
        .format(value)
}

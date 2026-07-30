package com.maik205.shoumeiplayer.ui.television.screens.live

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maik205.shoumeiplayer.data.ApiResult
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.data.repo.LibraryRepository
import com.maik205.shoumeiplayer.ui.television.model.MediaItemUi
import com.maik205.shoumeiplayer.ui.television.model.toTelevisionUi
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    val timeLabel: String,
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
    val channels: List<LiveChannelUi> = emptyList(),
    val focused: LiveProgramUi? = null,
    val windowStart: Instant = Instant.now(),
    val windowEnd: Instant = Instant.now().plusSeconds(6 * 60 * 60),
    val error: String? = null,
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
            _state.update { it.copy(loading = true, error = null) }
            val now = OffsetDateTime.now()
            val windowStart = now.minusMinutes(30).withSecond(0).withNano(0)
            val windowEnd = windowStart.plusHours(6)
            val result = coroutineScope {
                val channels = async { repository.liveTvChannels(limit = 160) }
                val programs = async {
                    repository.liveTvPrograms(
                        minStartDate = windowStart.toInstant().toString(),
                        maxStartDate = windowEnd.toInstant().toString(),
                        limit = 1_000,
                    )
                }
                channels.await() to programs.await()
            }
            val channelResult = result.first
            val programResult = result.second
            if (channelResult is ApiResult.Failure) {
                _state.update {
                    it.copy(
                        loading = false,
                        error = channelResult.error.displayMessage,
                    )
                }
                return@launch
            }

            val channelItems = (channelResult as ApiResult.Success).data.items
            val programs = (programResult as? ApiResult.Success)?.data?.items.orEmpty()
                .mapNotNull(::programUi)
            val grouped = programs.groupBy(LiveProgramUi::channelId)
            val channels = channelItems.map { channel ->
                val scheduled = grouped[channel.id].orEmpty().ifEmpty {
                    listOfNotNull(channel.currentProgram?.let(::programUi))
                }
                LiveChannelUi(
                    item = channel.toTelevisionUi(images),
                    number = channel.channelNumber,
                    programs = scheduled.sortedBy(LiveProgramUi::start),
                )
            }
            val nowInstant = Instant.now()
            val focused = channels.asSequence()
                .flatMap { it.programs.asSequence() }
                .firstOrNull { nowInstant >= it.start && nowInstant < it.end }
                ?: channels.firstNotNullOfOrNull { it.programs.firstOrNull() }
            _state.value = TelevisionLiveState(
                loading = false,
                channels = channels,
                focused = focused,
                windowStart = windowStart.toInstant(),
                windowEnd = windowEnd.toInstant(),
            )
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
            timeLabel = "${clock(start)} to ${clock(end)}",
        )
    }

    private fun parseInstant(value: String): Instant? = runCatching {
        OffsetDateTime.parse(value).toInstant()
    }.recoverCatching {
        Instant.parse(value)
    }.getOrNull()

    private fun clock(value: Instant): String = DateTimeFormatter
        .ofPattern("HH:mm")
        .withZone(ZoneId.systemDefault())
        .format(value)
}

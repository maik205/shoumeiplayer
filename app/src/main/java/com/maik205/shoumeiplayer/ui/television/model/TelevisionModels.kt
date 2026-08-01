package com.maik205.shoumeiplayer.ui.television.model

import androidx.compose.runtime.Immutable
import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.domain.result.mapping.toMediaItem
import com.maik205.shoumeiplayer.domain.model.MediaItem

@Immutable
data class HeroUi(
    val item: MediaItem,
    val eyebrow: String? = null,
    val actionLabel: String = if (item.resumeTicks > 0) "Resume" else "Play",
)

/**
 * Compatibility mapper for detail/player features that still receive Jellyfin DTOs.
 * Browse and search consume [com.maik205.shoumeiplayer.domain.repository.MediaCatalog] instead.
 */
fun BaseItemDto.toTelevisionUi(images: ImageUrlBuilder): MediaItem = toMediaItem(images)

package com.maik205.shoumeiplayer.domain.result.mapping

import com.maik205.shoumeiplayer.data.ImageUrlBuilder
import com.maik205.shoumeiplayer.data.api.dto.BaseItemDto
import com.maik205.shoumeiplayer.domain.model.DetailItem
import com.maik205.shoumeiplayer.domain.model.DetailMediaStream
import com.maik205.shoumeiplayer.domain.model.DetailPerson

fun BaseItemDto.toDetailItem(images: ImageUrlBuilder): DetailItem =
    DetailItem(
        media = toMediaItem(images),
        id = id,
        name = name.orEmpty(),
        type = type,
        productionYear = productionYear,
        premiereDate = premiereDate,
        communityRating = communityRating,
        officialRating = officialRating,
        indexNumber = indexNumber,
        parentIndexNumber = parentIndexNumber,
        seriesId = seriesId,
        seriesName = seriesName,
        seasonId = seasonId,
        seasonName = seasonName,
        episodeCount = episodeCount,
        genres = genres,
        runTimeTicks = runTimeTicks,
        status = status,
        channelId = channelId,
        studios = studios.mapNotNull { it.name?.takeIf(String::isNotBlank) },
        people = people.map { person ->
            DetailPerson(
                id = person.id,
                name = person.name.orEmpty(),
                role = person.role,
                type = person.type,
                imageUrl = person.primaryImageTag?.let {
                    images.personPrimary(person.id, it, maxWidth = 480)
                },
            )
        },
        mediaStreams = mediaStreams.map {
            DetailMediaStream(
                index = it.index,
                type = it.type,
                codec = it.codec,
                language = it.language,
                displayTitle = it.displayTitle,
                channels = it.channels,
                width = it.width,
                height = it.height,
                isDefault = it.isDefault,
            )
        },
        favorite = userData?.isFavorite == true,
        played = userData?.played == true,
        resumeTicks = userData?.playbackPositionTicks ?: 0,
    )

package com.maik205.shoumeiplayer.feature.player

class QueueNavigator {
    fun previousEpisode(previousEpisodeId: String?): String? = previousEpisodeId

    fun nextEpisode(nextEpisodeId: String?): String? = nextEpisodeId

    fun previousAudio(queue: List<AudioQueueItemUi>): String? {
        val currentIndex = queue.indexOfFirst(AudioQueueItemUi::playing)
        return queue.getOrNull(currentIndex - 1)?.itemId
    }

    fun nextAudio(queue: List<AudioQueueItemUi>): String? {
        val currentIndex = queue.indexOfFirst(AudioQueueItemUi::playing)
        return queue.getOrNull(currentIndex + 1)?.itemId
    }

    fun upNext(upNext: UpNextUi?): String? = upNext?.itemId
}

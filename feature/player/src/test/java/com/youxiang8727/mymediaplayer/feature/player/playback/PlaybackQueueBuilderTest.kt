package com.youxiang8727.mymediaplayer.feature.player.playback

import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueBuilderTest {

    private val playlist = listOf(
        PlaylistItem(videoId = "aaa", title = "晴天", thumbnailUrl = "", playlistId = 0L),
        PlaylistItem(videoId = "bbb", title = "七里香", thumbnailUrl = "", playlistId = 0L),
        PlaylistItem(videoId = "ccc", title = "擱淺", thumbnailUrl = "", playlistId = 0L)
    )

    @Test
    fun `清單為空時 回傳單曲佇列`() {
        val queue = PlaybackQueueBuilder.build(emptyList(), "xxx", "單曲")

        assertEquals(1, queue.entries.size)
        assertEquals("xxx", queue.entries.single().videoId)
        assertEquals(0, queue.startIndex)
    }

    @Test
    fun `點播的歌在清單中 佇列為整份清單且從該曲開始`() {
        val queue = PlaybackQueueBuilder.build(playlist, "bbb", "七里香")

        assertEquals(listOf("aaa", "bbb", "ccc"), queue.entries.map { it.videoId })
        assertEquals(1, queue.startIndex)
    }

    @Test
    fun `點播的歌不在清單中 回傳單曲佇列`() {
        val queue = PlaybackQueueBuilder.build(playlist, "ddd", "新歌")

        assertEquals(1, queue.entries.size)
        assertEquals("ddd", queue.entries.single().videoId)
        assertEquals(0, queue.startIndex)
    }

    @Test
    fun `標題空白時 以 videoId 作為標題`() {
        val queue = PlaybackQueueBuilder.build(emptyList(), "xxx", "")

        assertEquals("xxx", queue.entries.single().title)
    }

    @Test
    fun `RepeatMode 在 ALL 與 ONE 間循環`() {
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.ALL, RepeatMode.ONE.next())
    }
}

package com.youxiang8727.mymediaplayer.feature.player.playback

import com.youxiang8727.mymediaplayer.core.domain.model.PlaylistItem
import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.RepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueBuilderTest {

    private val playlist = listOf(
        PlaylistItem(videoId = "aaa", title = "晴天", thumbnailUrl = "", playlistId = 0L),
        PlaylistItem(videoId = "bbb", title = "七里香", thumbnailUrl = "", playlistId = 0L),
        PlaylistItem(videoId = "ccc", title = "擱淺", thumbnailUrl = "", playlistId = 0L)
    )

    private val entries = listOf(
        PlayQueueItem(videoId = "e1", title = "第一"),
        PlayQueueItem(videoId = "e2", title = "第二"),
        PlayQueueItem(videoId = "e3", title = "第三")
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

    // ── buildFromEntries（暫時性佇列：熱門榜單等非 Room 清單）──

    @Test
    fun `buildFromEntries 從索引中段起播 佇列完整保留`() {
        val queue = PlaybackQueueBuilder.buildFromEntries(entries, startIndex = 1)

        assertEquals(listOf("e1", "e2", "e3"), queue.entries.map { it.videoId })
        assertEquals(1, queue.startIndex)
    }

    @Test
    fun `buildFromEntries 索引 0 從頭起播`() {
        val queue = PlaybackQueueBuilder.buildFromEntries(entries, startIndex = 0)

        assertEquals(3, queue.entries.size)
        assertEquals(0, queue.startIndex)
    }

    @Test
    fun `buildFromEntries 索引超出清單尾端時 clamp 到最後一筆`() {
        val queue = PlaybackQueueBuilder.buildFromEntries(entries, startIndex = entries.size)

        assertEquals(entries.size - 1, queue.startIndex)
        assertEquals("e3", queue.entries[queue.startIndex].videoId)
    }

    @Test
    fun `buildFromEntries 負索引 clamp 到 0`() {
        val queue = PlaybackQueueBuilder.buildFromEntries(entries, startIndex = -3)

        assertEquals(0, queue.startIndex)
    }

    @Test
    fun `buildFromEntries 空清單回傳 size 0 佇列`() {
        val queue = PlaybackQueueBuilder.buildFromEntries(emptyList(), startIndex = 0)

        assertTrue(queue.entries.isEmpty())
        assertEquals(0, queue.startIndex)
    }

    @Test
    fun `buildFromEntries 標題空白時 以 videoId 作為標題`() {
        val queue = PlaybackQueueBuilder.buildFromEntries(
            listOf(PlayQueueItem("x1", "")),
            startIndex = 0
        )

        assertEquals("x1", queue.entries.single().title)
    }

    @Test
    fun `Room 路徑 build 不受暫時性佇列影響`() {
        // build 仍是「整份清單起播／否則單曲」語意（與新增前行為一致）
        val inPlaylist = PlaybackQueueBuilder.build(playlist, "ccc", "擱淺")
        assertEquals(listOf("aaa", "bbb", "ccc"), inPlaylist.entries.map { it.videoId })
        assertEquals(2, inPlaylist.startIndex)

        val single = PlaybackQueueBuilder.build(playlist, "zzz", "不在清單")
        assertEquals(listOf("zzz"), single.entries.map { it.videoId })
        assertEquals(0, single.startIndex)
    }

    @Test
    fun `RepeatMode 在 ALL 與 ONE 間循環`() {
        assertEquals(RepeatMode.ONE, RepeatMode.ALL.next())
        assertEquals(RepeatMode.ALL, RepeatMode.ONE.next())
    }
}

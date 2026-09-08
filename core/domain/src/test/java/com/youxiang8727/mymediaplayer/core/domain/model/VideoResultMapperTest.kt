package com.youxiang8727.mymediaplayer.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 驗證 VideoResult → PlaylistItem 的 duration 欄位傳遞（跨層 contract 的一部分：
 * 搜尋/熱門榜單解析出的時長必須隨 toPlaylistItem 帶進播放清單）。
 */
class VideoResultMapperTest {

    @Test
    fun `toPlaylistItem 保留 duration 欄位`() {
        val video = VideoResult("id1", "晴天", "https://img/1", "Jay Chou", "3:45")

        val item = video.toPlaylistItem(playlistId = 7L)

        assertEquals("3:45", item.duration)
        assertEquals(7L, item.playlistId)
    }

    @Test
    fun `duration 為 null 時 toPlaylistItem 亦為 null`() {
        val video = VideoResult("id1", "晴天", "https://img/1", "Jay Chou")

        val item = video.toPlaylistItem()

        assertNull(item.duration)
    }
}
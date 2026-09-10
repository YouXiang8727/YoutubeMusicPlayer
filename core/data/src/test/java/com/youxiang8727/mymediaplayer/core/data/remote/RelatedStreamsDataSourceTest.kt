package com.youxiang8727.mymediaplayer.core.data.remote

import com.youxiang8727.mymediaplayer.core.domain.model.VideoResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/**
 * 純 JVM 驗證 [RelatedStreamsDataSource] 的解析映射純函數：
 * [relatedItemToVideoResult]（含 null 防禦）、[extractVideoIdFromUrl]、
 * [formatDurationSeconds]。
 */
class RelatedStreamsDataSourceTest {

    private fun streamItem(
        url: String,
        name: String,
        uploader: String = "上傳者",
        duration: Long = 225L,
        thumbnails: List<Image> = listOf(
            Image("https://img/thumb.jpg", 480, 270, Image.ResolutionLevel.MEDIUM)
        )
    ) = StreamInfoItem(0, url, name, StreamType.VIDEO_STREAM).apply {
        setUploaderName(uploader)
        setDuration(duration)
        setThumbnails(thumbnails)
    }

    // ── relatedItemToVideoResult：正常映射 ──

    @Test
    fun `StreamInfoItem 完整映射為 VideoResult`() {
        val result = relatedItemToVideoResult(
            streamItem(
                url = "https://www.youtube.com/watch?v=abc123def45",
                name = "晴天",
                uploader = "周杰倫",
                duration = 225L
            )
        )

        assertEquals(
            VideoResult(
                videoId = "abc123def45",
                title = "晴天",
                thumbnailUrl = "https://img/thumb.jpg",
                channel = "周杰倫",
                duration = "3:45"
            ),
            result
        )
    }

    @Test
    fun `youtu-be 短網址也能萃取出 videoId`() {
        val result = relatedItemToVideoResult(
            streamItem(url = "https://youtu.be/abc123def45", name = "歌名")
        )

        assertEquals("abc123def45", result?.videoId)
    }

    @Test
    fun `shorts URL 也能萃取出 videoId`() {
        val result = relatedItemToVideoResult(
            streamItem(url = "https://www.youtube.com/shorts/abc123def45", name = "歌名")
        )

        assertEquals("abc123def45", result?.videoId)
    }

    // ── relatedItemToVideoResult：null 防禦 ──

    @Test
    fun `非 StreamInfoItem 型別回 null`() {
        val channelItem = ChannelInfoItem(0, "https://www.youtube.com/channel/UC123", "頻道")

        assertNull(relatedItemToVideoResult(channelItem))
    }

    @Test
    fun `null 輸入回 null`() {
        assertNull(relatedItemToVideoResult(null))
    }

    @Test
    fun `URL 無法萃取出 videoId 回 null`() {
        val broken = streamItem(url = "https://example.com/not-a-video", name = "歌名")

        assertNull(relatedItemToVideoResult(broken))
    }

    @Test
    fun `無縮圖時 thumbnailUrl 為空字串`() {
        val noThumb = streamItem(url = "https://www.youtube.com/watch?v=abc123def45", name = "歌名", thumbnails = emptyList())

        assertEquals("", relatedItemToVideoResult(noThumb)?.thumbnailUrl)
    }

    @Test
    fun `name 為空白時以 videoId 當 title`() {
        val blankName = streamItem(url = "https://www.youtube.com/watch?v=abc123def45", name = "   ")

        assertEquals("abc123def45", relatedItemToVideoResult(blankName)?.title)
    }

    // ── formatDurationSeconds ──

    @Test
    fun `秒數轉 mm-ss 格式`() {
        assertEquals("3:45", formatDurationSeconds(225))
        assertEquals("3:05", formatDurationSeconds(185))
        assertEquals("12:34", formatDurationSeconds(754))
    }

    @Test
    fun `超過一小時轉 h-mm-ss 格式`() {
        assertEquals("1:02:03", formatDurationSeconds(3723))
    }

    @Test
    fun `秒數小於等於零回 null（未知或直播）`() {
        assertNull(formatDurationSeconds(0))
        assertNull(formatDurationSeconds(-1))
    }

    // ── extractVideoIdFromUrl ──

    @Test
    fun `extractVideoIdFromUrl 支援多種 URL 形態`() {
        assertEquals("abc123def45", extractVideoIdFromUrl("https://www.youtube.com/watch?v=abc123def45"))
        assertEquals("abc123def45", extractVideoIdFromUrl("https://youtu.be/abc123def45"))
        assertEquals("abc123def45", extractVideoIdFromUrl("https://www.youtube.com/shorts/abc123def45"))
        assertEquals("abc123def45", extractVideoIdFromUrl("https://www.youtube.com/embed/abc123def45"))
        assertEquals("abc123def45", extractVideoIdFromUrl("https://www.youtube.com/watch?v=abc123def45&t=30"))
    }

    @Test
    fun `extractVideoIdFromUrl 無法萃取回 null`() {
        assertNull(extractVideoIdFromUrl(null))
        assertNull(extractVideoIdFromUrl("https://www.youtube.com/playlist?list=PL123"))
        assertNull(extractVideoIdFromUrl(""))
    }
}
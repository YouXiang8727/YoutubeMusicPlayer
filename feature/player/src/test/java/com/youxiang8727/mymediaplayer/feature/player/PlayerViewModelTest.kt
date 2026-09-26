package com.youxiang8727.mymediaplayer.feature.player

import com.youxiang8727.mymediaplayer.core.domain.model.PlayQueueItem
import com.youxiang8727.mymediaplayer.core.domain.model.PlaybackSnapshot
import com.youxiang8727.mymediaplayer.core.domain.model.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PlayerViewModel.onPlaybackIntent] 的意圖分送契約測試。
 *
 * onPlaybackIntent 是同步純轉送（不碰 dispatcher、不做 coroutine suspend），
 * 故以 FakePlayerController 記錄呼叫即可斷言；ViewModel 只依賴 PlayerController 介面，
 * 不需 Robolectric、不需注入 Hilt。
 */
class PlayerViewModelTest {

    /** 記錄 addToQueue / play / playQueue / seekToIndex / clearQueue 的呼叫供斷言；其餘空實作。 */
    private class FakePlayerController : PlayerController {
        var addToQueueCall: Pair<String, String>? = null
        var playCall: Pair<String, String>? = null
        var playQueueCall: Pair<List<PlayQueueItem>, Int>? = null
        var seekToIndexCall: Int? = null
        var clearQueueCalled: Boolean = false

        override val playback = MutableStateFlow(PlaybackSnapshot())
        override val queue = MutableStateFlow<List<PlayQueueItem>>(emptyList())

        override fun addToQueue(videoId: String, title: String) {
            addToQueueCall = videoId to title
        }

        override fun play(videoId: String, title: String) {
            playCall = videoId to title
        }

        override fun playQueue(items: List<PlayQueueItem>, startIndex: Int) {
            playQueueCall = items to startIndex
        }

        override fun seekToIndex(index: Int) {
            seekToIndexCall = index
        }

        override fun clearQueue() {
            clearQueueCalled = true
        }

        override fun togglePlayPause() {}
        override fun seekToNext() {}
        override fun seekToPrevious() {}
        override fun seekTo(positionMs: Long) {}
        override fun toggleShuffle() {}
        override fun cycleRepeatMode() {}
        override fun stop() {}
        override fun removeFromQueue(index: Int) {}
    }

    private class Harness(val vm: PlayerViewModel, val player: FakePlayerController)

    private fun buildHarness(): Harness {
        val player = FakePlayerController()
        return Harness(PlayerViewModel(player), player)
    }

    // ── AddToQueue（新增「加入佇列」路徑）──

    @Test
    fun `AddToQueue 轉送 videoId 與 title 給 addToQueue`() {
        val h = buildHarness()

        h.vm.onPlaybackIntent(PlaybackIntent.AddToQueue("id1", "title1"))

        assertEquals("id1" to "title1", h.player.addToQueueCall)
    }

    @Test
    fun `AddToQueue 不觸發 play（加入佇列不中斷目前播放）`() {
        val h = buildHarness()

        h.vm.onPlaybackIntent(PlaybackIntent.AddToQueue("id1", "title1"))

        assertNull(h.player.playCall)
        assertNull(h.player.playQueueCall)
    }

    // ── 既有 intent 回歸測底 ──

    @Test
    fun `Play 轉送 videoId 與 title 給 play`() {
        val h = buildHarness()

        h.vm.onPlaybackIntent(PlaybackIntent.Play("id9", "t9"))

        assertEquals("id9" to "t9", h.player.playCall)
        assertNull(h.player.addToQueueCall)
    }

    @Test
    fun `PlayList 轉送 entries 與 startIndex 給 playQueue`() {
        val h = buildHarness()
        val entries = listOf(PlayQueueItem("e1", "第一"), PlayQueueItem("e2", "第二"))

        h.vm.onPlaybackIntent(PlaybackIntent.PlayList(entries, startIndex = 1))

        assertEquals(entries to 1, h.player.playQueueCall)
        assertNull(h.player.playCall)
    }

    @Test
    fun `ClearQueue 轉送給 clearQueue`() {
        val h = buildHarness()

        h.vm.onPlaybackIntent(PlaybackIntent.ClearQueue)

        assertTrue(h.player.clearQueueCalled)
    }

    @Test
    fun `SeekToIndex 轉送 index 給 seekToIndex`() {
        val h = buildHarness()

        h.vm.onPlaybackIntent(PlaybackIntent.SeekToIndex(2))

        assertEquals(2, h.player.seekToIndexCall)
    }
}

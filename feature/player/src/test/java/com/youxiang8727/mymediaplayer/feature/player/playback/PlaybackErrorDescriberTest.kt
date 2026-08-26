package com.youxiang8727.mymediaplayer.feature.player.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackErrorDescriberTest {

    @Test
    fun `cause chain 深層的聚合中文訊息優先`() {
        val message = PlaybackErrorDescriber.describe(
            errorCodeName = "ERROR_CODE_IO_UNSPECIFIED",
            causeChainMessages = listOf(
                "解析串流失敗：所有解析來源皆失敗（疑似遭 YouTube bot 封鎖，可稍後重試或更換網路）" +
                    " [NewPipe: ...；InnerTube: ...；Piped: HTTP 525]",
                "所有解析來源皆失敗（疑似遭 YouTube bot 封鎖，可稍後重試或更換網路）"
            )
        )

        assertEquals(
            "播放失敗：所有解析來源皆失敗（疑似遭 YouTube bot 封鎖，可稍後重試或更換網路）",
            message
        )
    }

    @Test
    fun `無 cause 訊息時 以 errorCodeName 人類可讀化兜底`() {
        val message = PlaybackErrorDescriber.describe("ERROR_CODE_IO_UNSPECIFIED", emptyList())

        assertEquals("播放失敗：IO Unspecified", message)
    }

    @Test
    fun `空白與 null 訊息跳過 取最深的有效訊息`() {
        val message = PlaybackErrorDescriber.describe(
            errorCodeName = "ERROR_CODE_TIMEOUT",
            causeChainMessages = listOf(null, "", "   ", "深層有效訊息")
        )

        assertEquals("播放失敗：深層有效訊息", message)
    }

    @Test
    fun `全部訊息皆空白時 回退到 errorCodeName`() {
        val message = PlaybackErrorDescriber.describe(
            errorCodeName = "ERROR_CODE_DRM_SCHEME_UNSUPPORTED",
            causeChainMessages = listOf(null, "  ")
        )

        assertEquals("播放失敗：DRM Scheme Unsupported", message)
    }

    @Test
    fun `最深層有效訊息會去頭尾空白`() {
        val message = PlaybackErrorDescriber.describe(
            errorCodeName = "ERROR_CODE_IO_UNSPECIFIED",
            causeChainMessages = listOf(null, "  解析串流失敗  ")
        )

        assertEquals("播放失敗：解析串流失敗", message)
    }
}

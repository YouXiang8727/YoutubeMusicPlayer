package com.youxiang8727.mymediaplayer.core.data.remote.stream

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackStreamResolverTest {

    /** 可編程的假來源：回傳 handler 結果，並記錄每次呼叫以驗證嘗試順序與次數。 */
    private class FakeSource(
        override val name: String,
        private val handler: (String) -> Result<String>
    ) : AudioStreamSource {
        val calls = mutableListOf<String>()
        override suspend fun fetch(videoId: String): Result<String> {
            calls += videoId
            return handler(videoId)
        }
    }

    /** 可推進的假時鐘。 */
    private class FakeClock(var now: Long = 0L) : StreamClock {
        override fun nowMs(): Long = now
    }

    /** 測試用 dispatcher：全用 Default，避免 Main 在 JVM 測試環境不存在。 */
    private object TestDispatchers : DispatcherProvider {
        override val io: CoroutineDispatcher = Dispatchers.Default
        override val default: CoroutineDispatcher = Dispatchers.Default
        override val main: CoroutineDispatcher = Dispatchers.Default
    }

    private val classifier = StreamErrorClassifier()

    private fun resolver(sources: List<AudioStreamSource>, clock: StreamClock) =
        FallbackStreamResolver(sources, classifier, TestDispatchers, clock)

    @Test
    fun `主路徑成功時不觸發後續來源`() = runTest {
        val primary = FakeSource("NewPipe") { Result.success("https://primary/url") }
        val secondary = FakeSource("InnerTube") { Result.success("https://secondary/url") }

        val result = resolver(listOf(primary, secondary), FakeClock()).resolve("abc")

        assertEquals("https://primary/url", result.getOrThrow())
        assertTrue(secondary.calls.isEmpty())
    }

    @Test
    fun `主路徑失敗時依序退到下一來源並成功`() = runTest {
        val primary = FakeSource("NewPipe") {
            Result.failure(IOException("playabilityStatus=LOGIN_REQUIRED"))
        }
        val secondary = FakeSource("InnerTube") { Result.success("https://innertube/url") }
        val last = FakeSource("Piped") { Result.success("https://piped/url") }

        val result = resolver(listOf(primary, secondary, last), FakeClock()).resolve("abc")

        assertEquals("https://innertube/url", result.getOrThrow())
        assertEquals(listOf("abc"), primary.calls)
        assertEquals(listOf("abc"), secondary.calls)
        // 第三層不該被呼叫：第二層已成功
        assertTrue(last.calls.isEmpty())
    }

    @Test
    fun `全部失敗時聚合各來源錯誤並標注 bot 封鎖`() = runTest {
        val sources = listOf(
            FakeSource("NewPipe") {
                Result.failure(IOException("Sign in to confirm that you're not a bot"))
            },
            FakeSource("InnerTube") {
                Result.failure(IOException("playabilityStatus=LOGIN_REQUIRED（Sign in to confirm）"))
            },
            FakeSource("Piped") { Result.failure(IOException("HTTP 503")) }
        )

        val result = resolver(sources, FakeClock()).resolve("abc")

        val message = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(message.contains("所有解析來源皆失敗"))
        assertTrue(message.contains("bot 封鎖"))
        listOf("NewPipe", "InnerTube", "Piped").forEach { assertTrue(message.contains(it)) }
    }

    @Test
    fun `快取命中時不再呼叫任何來源`() = runTest {
        val primary = FakeSource("NewPipe") { Result.success("https://cached/url") }
        val clock = FakeClock()
        val fallback = resolver(listOf(primary), clock)

        fallback.resolve("abc")
        val second = fallback.resolve("abc")

        assertEquals("https://cached/url", second.getOrThrow())
        assertEquals(1, primary.calls.size)
    }

    @Test
    fun `快取逾時後重新解析`() = runTest {
        val primary = FakeSource("NewPipe") { Result.success("https://fresh/url") }
        val clock = FakeClock()
        val fallback = resolver(listOf(primary), clock)

        fallback.resolve("abc")
        clock.now += FallbackStreamResolver.CACHE_TTL_MS + 1
        fallback.resolve("abc")

        assertEquals(2, primary.calls.size)
    }

    @Test
    fun `force 繞過 TTL 快取強制重解析`() = runTest {
        val primary = FakeSource("NewPipe") { Result.success("https://fresh/url") }
        val clock = FakeClock()
        val fallback = resolver(listOf(primary), clock)

        // 第一次解析成功，寫入快取
        fallback.resolve("abc")
        assertEquals(1, primary.calls.size)

        // force=true 時即使 TTL 內也強制重跑來源
        fallback.resolve("abc", force = true)

        assertEquals(2, primary.calls.size)
    }

    @Test
    fun `force 全數失敗時不保留舊快取`() = runTest {
        // 用可切換 handler 的假來源：先成功後改失敗
        var shouldFail = false
        val source = FakeSource("NewPipe") {
            if (shouldFail) Result.failure(IOException("HTTP 403"))
            else Result.success("https://old/url")
        }
        val clock = FakeClock()
        val fallback = resolver(listOf(source), clock)

        // 先寫入快取
        fallback.resolve("abc")
        assertEquals(1, source.calls.size)

        // 來源開始失敗（模擬舊 URL 失效後重新解析也失敗），force=true 觸發重解析
        shouldFail = true
        val forced = fallback.resolve("abc", force = true)

        assertTrue(forced.isFailure)
        // 舊快取不應殘留：之後一般（非 force）呼叫不該命中已死的舊 URL
        shouldFail = false
        val after = fallback.resolve("abc")
        // force 失敗已清除快取 → 非 force 呼叫會重新解析（走成功 path）
        assertEquals("https://old/url", after.getOrThrow())
        assertEquals(3, source.calls.size)
    }

    @Test
    fun `raw HTTP 403 被分類為 TRANSIENT 而非 PERMANENT`() {
        val classifier = StreamErrorClassifier()
        assertEquals(
            StreamFailureKind.TRANSIENT,
            classifier.classify("HTTP 403")
        )
        assertEquals(
            StreamFailureKind.TRANSIENT,
            classifier.classify("Response code: 403")
        )
        assertEquals(
            StreamFailureKind.TRANSIENT,
            classifier.classify("HTTP 403（實例可能過載或被封鎖）")
        )
        // 403 字元邊界：4030 不應誤判
        assertEquals(
            StreamFailureKind.PERMANENT,
            classifier.classify("HTTP 4030")
        )
    }

    @Test
    fun `403 不會被併入 5xx 網路關鍵字邏輯誤判為 PERMANENT`() {
        val classifier = StreamErrorClassifier()
        // "http 403" 同時含 "http 5"（前綴），要確保 403 規則優先命中。
        // \b403\b 在 "http 403" 會命中 403 → TRANSIENT；若誤走 NETWORK 的 "http 5" 也是 TRANSIENT，
        // 故斷言結果一致為 TRANSIENT。
        assertEquals(StreamFailureKind.TRANSIENT, classifier.classify("HTTP 403"))
    }

    @Test
    fun `describe 依分類給出不同提示`(){
        val classifier = StreamErrorClassifier()

        // bot 封鎖提示：任一來源含 bot 關鍵字
        val bot = classifier.describe(
            listOf(
                "NewPipe" to IOException("Sign in to confirm that you're not a bot"),
                "Piped" to IOException("HTTP 503")
            )
        )
        assertTrue(bot.contains("bot 封鎖"))

        // 403／連結失效提示：任一來源含 403
        val expired = classifier.describe(
            listOf(
                "NewPipe" to IOException("HTTP 403"),
                "Piped" to IOException("HTTP 503")
            )
        )
        assertTrue(expired.contains("播放連結已失效"))

        // 全暫時性（網路）提示
        val transient = classifier.describe(
            listOf(
                "NewPipe" to IOException("timeout"),
                "Piped" to IOException("connection reset")
            )
        )
        assertTrue(transient.contains("網路不穩"))

        // 一般錯誤
        val permanent = classifier.describe(
            listOf(
                "NewPipe" to IOException("此影片沒有可用的音訊串流")
            )
        )
        assertTrue(permanent.contains("所有解析來源皆失敗"))
        assertFalse(permanent.contains("bot 封鎖"))
        assertFalse(permanent.contains("播放連結已失效"))
        assertFalse(permanent.contains("網路不穩"))
    }
}

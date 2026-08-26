package com.youxiang8727.mymediaplayer.core.data.remote.stream

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}

package com.youxiang8727.mymediaplayer.core.data.remote.stream

import com.youxiang8727.mymediaplayer.core.common.DispatcherProvider
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** [StreamHttpTransport] 的 OkHttp 實作：共用 NetworkModule 的 client（UA/逾時一致）。 */
@Singleton
class OkHttpStreamHttpTransport @Inject constructor(
    private val client: OkHttpClient,
    private val dispatchers: DispatcherProvider
) : StreamHttpTransport {

    override suspend fun execute(request: StreamHttpRequest): StreamHttpResponse =
        withContext(dispatchers.io) {
            val builder = Request.Builder().url(request.url)

            when (request.method.uppercase()) {
                "POST" -> builder.post(
                    (request.body ?: "").toRequestBody("application/json".toMediaType())
                )
                else -> builder.get()
            }
            request.headers.forEach { (name, value) -> builder.header(name, value) }

            client.newCall(builder.build()).execute().use { response ->
                StreamHttpResponse(code = response.code, body = response.body?.string())
            }
        }
}

package com.youxiang8727.mymediaplayer.core.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest

/** 讓 NewPipe Extractor 透過共用的 OkHttpClient 發送請求。 */
class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: ExtractorRequest): ExtractorResponse {
        val builder = Request.Builder().url(request.url())

        when (request.httpMethod().uppercase()) {
            "GET" -> builder.get()
            "HEAD" -> builder.head()
            "POST" -> builder.post(
                (request.dataToSend() ?: ByteArray(0)).toRequestBody(null)
            )
            else -> builder.method(
                request.httpMethod(),
                request.dataToSend()?.toRequestBody(null)
            )
        }

        request.headers().forEach { (name, values) ->
            values.forEach { builder.header(name, it) }
        }

        val response = client.newCall(builder.build()).execute()
        val body = response.body?.string()

        return ExtractorResponse(
            response.code,
            response.message,
            response.headers.toMultimap(),
            body,
            response.request.url.toString()
        )
    }
}

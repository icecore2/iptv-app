package com.iptv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.util.concurrent.TimeUnit

interface NetworkClient {
    suspend fun openStream(url: String, headers: Map<String, String> = emptyMap()): InputStream
}

class OkHttpNetworkClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : NetworkClient {

    override suspend fun openStream(url: String, headers: Map<String, String>): InputStream = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        
        // Default User-Agent to avoid common IPTV provider 403 blocks
        var hasUserAgent = false
        for ((key, value) in headers) {
            requestBuilder.addHeader(key, value)
            if (key.equals("User-Agent", ignoreCase = true)) {
                hasUserAgent = true
            }
        }
        if (!hasUserAgent) {
            requestBuilder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IllegalArgumentException("HTTP error ${response.code}: ${response.message}")
        }
        val body = response.body ?: throw IllegalStateException("Empty response body from $url")
        body.byteStream()
    }
}

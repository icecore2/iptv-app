package com.iptv.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.InputStream
import java.util.concurrent.TimeUnit

interface NetworkClient {
    suspend fun openStream(url: String, headers: Map<String, String> = emptyMap()): InputStream
    suspend fun postForm(url: String, formParams: Map<String, String>, headers: Map<String, String> = emptyMap()): InputStream
    suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String> = emptyMap()): InputStream
}

class OkHttpNetworkClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()
) : NetworkClient {

    private fun applyHeaders(builder: Request.Builder, headers: Map<String, String>) {
        var hasUserAgent = false
        for ((key, value) in headers) {
            builder.addHeader(key, value)
            if (key.equals("User-Agent", ignoreCase = true)) {
                hasUserAgent = true
            }
        }
        if (!hasUserAgent) {
            builder.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
        }
    }

    override suspend fun openStream(url: String, headers: Map<String, String>): InputStream = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder().url(url)
        applyHeaders(requestBuilder, headers)

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IllegalArgumentException("HTTP error ${response.code}: ${response.message}")
        }
        val body = response.body ?: throw IllegalStateException("Empty response body from $url")
        body.byteStream()
    }

    override suspend fun postForm(url: String, formParams: Map<String, String>, headers: Map<String, String>): InputStream = withContext(Dispatchers.IO) {
        val formBuilder = okhttp3.FormBody.Builder()
        for ((k, v) in formParams) {
            formBuilder.add(k, v)
        }
        val requestBuilder = Request.Builder()
            .url(url)
            .post(formBuilder.build())
        applyHeaders(requestBuilder, headers)

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IllegalArgumentException("HTTP error ${response.code}: ${response.message}")
        }
        val body = response.body ?: throw IllegalStateException("Empty response body from $url")
        body.byteStream()
    }

    override suspend fun postJson(url: String, jsonBody: String, headers: Map<String, String>): InputStream = withContext(Dispatchers.IO) {
        val reqBody = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val requestBuilder = Request.Builder()
            .url(url)
            .post(reqBody)
        applyHeaders(requestBuilder, headers)

        val response = client.newCall(requestBuilder.build()).execute()
        if (!response.isSuccessful) {
            throw IllegalArgumentException("HTTP error ${response.code}: ${response.message}")
        }
        val body = response.body ?: throw IllegalStateException("Empty response body from $url")
        body.byteStream()
    }
}

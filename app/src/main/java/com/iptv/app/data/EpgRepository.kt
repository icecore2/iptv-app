package com.iptv.app.data

import com.iptv.app.core.model.EpgData
import com.iptv.app.core.parser.XmlTvParser
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream

class EpgRepository(
    private val networkClient: NetworkClient = OkHttpNetworkClient(),
    private val parser: XmlTvParser = XmlTvParser(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    suspend fun loadEpgFromUrl(url: String, headers: Map<String, String> = emptyMap()): Result<EpgData> =
        withContext(ioDispatcher) {
            try {
                val isGzip = url.endsWith(".gz", ignoreCase = true)
                val stream = networkClient.openStream(url, headers)
                val epgData = parser.parse(stream, isGzip = if (isGzip) true else null)
                Result.success(epgData)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    fun loadEpgFromString(xml: String): EpgData {
        return parser.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }
}

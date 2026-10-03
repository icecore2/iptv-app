package com.iptv.app.core.metadata.providers

import com.iptv.app.core.metadata.MetadataSource
import com.iptv.app.core.metadata.ProgrammeMetadata

interface MetadataProvider {
    val source: MetadataSource
    suspend fun search(query: String, year: Int? = null, language: String = "en"): Result<ProgrammeMetadata?>
}

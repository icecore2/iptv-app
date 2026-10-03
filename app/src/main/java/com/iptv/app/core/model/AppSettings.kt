package com.iptv.app.core.model

import com.iptv.app.ui.viewmodel.AspectRatioMode

/**
 * Global application preferences and streaming settings.
 *
 * @param showChannelLogos Whether to load and display channel logos/thumbnails or use text/icon placeholders.
 * @param enablePagination Whether to paginate and lazy-load large channel lists in batches.
 * @param pageSize Number of channels to load per pagination batch (e.g. 25, 50, 100, 200).
 * @param showEpgInList Whether to display current/next programme title preview on channel cards.
 * @param autoLoadLastPlaylist Whether to auto-resume the last active playlist on startup.
 * @param bufferDurationSeconds ExoPlayer buffer duration preset in seconds (5, 15, 30, 60).
 * @param keepScreenOn Keep the device screen awake during video playback.
 * @param fastChannelSwitching Start playing immediately when selecting channels in selector sheet.
 * @param hardwareAcceleration Enable hardware video decoding/tunneling acceleration.
 * @param defaultAspectRatio Default aspect ratio mode on stream playback start.
 * @param showStreamInfoOverlay Display live bitrate, codecs, resolution info overlay on player.
 */
data class AppSettings(
    // General & Channel List
    val showChannelLogos: Boolean = true,
    val enablePagination: Boolean = true,
    val pageSize: Int = 50,
    val showEpgInList: Boolean = true,
    val autoLoadLastPlaylist: Boolean = false,

    // Player & Streaming
    val bufferDurationSeconds: Int = 15,
    val bufferStorageLimitMb: Int = 1024,
    val keepScreenOn: Boolean = true,
    val fastChannelSwitching: Boolean = true,
    val hardwareAcceleration: Boolean = true,
    val defaultAspectRatio: AspectRatioMode = AspectRatioMode.FIT,
    val showStreamInfoOverlay: Boolean = false,

    // Metadata & EPG Integrations (IMDb, Trakt, sratim.co.il, TVDB)
    val preferredMetadataSource: com.iptv.app.core.metadata.MetadataSource = com.iptv.app.core.metadata.MetadataSource.AUTO,
    val metadataLanguage: String = "en",
    val traktClientId: String = "",
    val tvdbApiKey: String = "",
    val showInlineMetadataBadge: Boolean = true
) {
    companion object {
        val BUFFER_PRESETS = listOf(
            5 to "5s (Low Latency / Live Sports)",
            15 to "15s (Balanced - Default)",
            30 to "30s (High Stability)",
            60 to "60s (Maximum Buffer / Weak WiFi)"
        )

        val BUFFER_STORAGE_PRESETS = listOf(
            256 to "256 MB",
            512 to "512 MB",
            1024 to "1 GB (Default)",
            2048 to "2 GB",
            4096 to "4 GB"
        )

        val PAGE_SIZE_OPTIONS = listOf(25, 50, 100, 200)

        val METADATA_LANGUAGE_OPTIONS = listOf(
            "en" to "English (en)",
            "he" to "עברית - Hebrew (he)",
            "es" to "Español - Spanish (es)",
            "fr" to "Français - French (fr)",
            "de" to "Deutsch - German (de)",
            "ru" to "Русский - Russian (ru)",
            "it" to "Italiano - Italian (it)",
            "ar" to "العربية - Arabic (ar)"
        )
    }
}

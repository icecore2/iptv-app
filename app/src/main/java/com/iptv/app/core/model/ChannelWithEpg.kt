package com.iptv.app.core.model

data class ChannelWithEpg(
    val channel: M3uItem,
    val currentProgramme: EpgProgramme? = null,
    val nextProgramme: EpgProgramme? = null,
    val progress: Float = 0f
)

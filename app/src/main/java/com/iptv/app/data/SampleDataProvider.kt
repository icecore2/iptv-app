package com.iptv.app.data

import com.iptv.app.core.model.EpgChannel
import com.iptv.app.core.model.EpgData
import com.iptv.app.core.model.EpgProgramme
import com.iptv.app.core.model.M3uItem
import com.iptv.app.core.model.M3uPlaylist

object SampleDataProvider {

    const val DEFAULT_SAMPLE_PLAYLIST_URL = "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us.m3u"
    const val DEFAULT_SAMPLE_EPG_URL = "https://raw.githubusercontent.com/iptv-org/epg/master/sites/tvguide.com/tvguide.com.epg.xml"

    fun getSamplePlaylist(): M3uPlaylist {
        val items = listOf(
            M3uItem(
                id = "nasa.tv",
                name = "NASA TV Public",
                streamUrl = "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8",
                group = "Science & Tech",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/e/e5/NASA_logo.svg",
                tvgId = "nasa.tv",
                tvgName = "NASA TV",
                catchup = "append",
                catchupSource = "?utc=${'$'}{start}&lutc=${'$'}{end}",
                catchupDays = 7
            ),
            M3uItem(
                id = "dw.news",
                name = "Deutsche Welle English",
                streamUrl = "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8",
                group = "News",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/7/75/Deutsche_Welle_logo.svg",
                tvgId = "dw.news",
                tvgName = "DW English",
                catchup = "append",
                catchupSource = "?utc=${'$'}{start}&lutc=${'$'}{end}",
                catchupDays = 7
            ),
            M3uItem(
                id = "france24.en",
                name = "France 24 English",
                streamUrl = "https://static.france24.com/live/F24_EN_LO_HLS/live_tv.m3u8",
                group = "News",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/8/82/France_24_logo.svg",
                tvgId = "france24.en",
                tvgName = "France 24",
                catchup = "append",
                catchupSource = "?utc=${'$'}{start}&lutc=${'$'}{end}",
                catchupDays = 7
            ),
            M3uItem(
                id = "redbull.tv",
                name = "Red Bull TV",
                streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
                group = "Sports",
                logoUrl = "https://upload.wikimedia.org/wikipedia/en/f/f5/Red_Bull_TV_logo.png",
                tvgId = "redbull.tv",
                tvgName = "Red Bull TV",
                catchup = "append",
                catchupSource = "?utc=${'$'}{start}&lutc=${'$'}{end}",
                catchupDays = 7
            ),
            M3uItem(
                id = "bloomberg.quicktake",
                name = "Bloomberg Quicktake",
                streamUrl = "https://bloomberg-quicktake-rakuten.amagi.tv/playlist.m3u8",
                group = "News",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/5/52/Bloomberg_Quicktake_logo.svg",
                tvgId = "bloomberg.quicktake",
                tvgName = "Bloomberg Quicktake",
                catchup = "append",
                catchupSource = "?utc=${'$'}{start}&lutc=${'$'}{end}",
                catchupDays = 7
            ),
            M3uItem(
                id = "bigbuckbunny",
                name = "Big Buck Bunny (HLS Test)",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                group = "Movies",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_buck_bunny_poster_big.jpg",
                tvgId = "bbb.test",
                tvgName = "Big Buck Bunny"
            ),
            M3uItem(
                id = "sintel",
                name = "Sintel HD (Animation Test)",
                streamUrl = "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                group = "Movies",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/9/90/Sintel_poster.jpg",
                tvgId = "sintel.test",
                tvgName = "Sintel"
            ),
            M3uItem(
                id = "tearsofsteel",
                name = "Tears of Steel (Sci-Fi Test)",
                streamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                group = "Movies",
                logoUrl = "https://upload.wikimedia.org/wikipedia/commons/9/9a/Tears_of_Steel_poster.jpg",
                tvgId = "tos.test",
                tvgName = "Tears of Steel"
            )
        )

        return M3uPlaylist(
            items = items,
            groups = items.map { it.group }.distinct(),
            epgUrl = DEFAULT_SAMPLE_EPG_URL,
            headerAttributes = mapOf("x-tvg-url" to DEFAULT_SAMPLE_EPG_URL)
        )
    }

    fun getSampleEpg(): EpgData {
        val now = System.currentTimeMillis()
        val oneHour = 3600_000L

        val channels = mapOf(
            "nasa.tv" to EpgChannel("nasa.tv", "NASA TV", "https://upload.wikimedia.org/wikipedia/commons/e/e5/NASA_logo.svg"),
            "dw.news" to EpgChannel("dw.news", "DW English", "https://upload.wikimedia.org/wikipedia/commons/7/75/Deutsche_Welle_logo.svg"),
            "france24.en" to EpgChannel("france24.en", "France 24", "https://upload.wikimedia.org/wikipedia/commons/8/82/France_24_logo.svg"),
            "redbull.tv" to EpgChannel("redbull.tv", "Red Bull TV", "https://upload.wikimedia.org/wikipedia/en/f/f5/Red_Bull_TV_logo.png"),
            "bbb.test" to EpgChannel("bbb.test", "Big Buck Bunny Cinema", null),
            "sintel.test" to EpgChannel("sintel.test", "Sintel Animation Theater", null),
            "tos.test" to EpgChannel("tos.test", "Sci-Fi Showcase", null)
        )

        val programmes = listOf(
            // NASA TV (Past Catchup VOD + Live + Upcoming)
            EpgProgramme(
                channelId = "nasa.tv",
                title = "Hubble Space Telescope: Deep Universe",
                startEpochMillis = now - (oneHour * 3),
                stopEpochMillis = now - (oneHour * 3 / 2),
                description = "Spectacular deep space imagery and cosmic discoveries by the Hubble Space Telescope.",
                category = "Science",
                iconUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa"
            ),
            EpgProgramme(
                channelId = "nasa.tv",
                title = "Mars Perseverance Rover: Jezero Crater Exploration",
                startEpochMillis = now - (oneHour * 3 / 2),
                stopEpochMillis = now - (oneHour / 2),
                description = "Astrobiologists analyze soil samples and search for past microbial life on the Red Planet.",
                category = "Science",
                iconUrl = "https://images.unsplash.com/photo-1614728894747-a83421e2b9c9"
            ),
            EpgProgramme(
                channelId = "nasa.tv",
                title = "ISS Space Station Live Feeds",
                startEpochMillis = now - (oneHour / 2),
                stopEpochMillis = now + (oneHour / 2),
                description = "Live views of Earth from the International Space Station with crew commentary.",
                category = "Science",
                iconUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa"
            ),
            EpgProgramme(
                channelId = "nasa.tv",
                title = "Artemis Lunar Mission Briefing",
                startEpochMillis = now + (oneHour / 2),
                stopEpochMillis = now + (oneHour * 2),
                description = "Deep dive into next-generation lunar exploration technologies.",
                category = "Science"
            ),
            // DW News (Past Catchup VOD + Live + Upcoming)
            EpgProgramme(
                channelId = "dw.news",
                title = "DW Conflict Zone & Geopolitics",
                startEpochMillis = now - (oneHour * 5 / 2),
                stopEpochMillis = now - (oneHour * 4 / 3),
                description = "Tough questions and hard talk on world politics and security challenges.",
                category = "News"
            ),
            EpgProgramme(
                channelId = "dw.news",
                title = "Eco Africa: Sustainable Solutions",
                startEpochMillis = now - (oneHour * 4 / 3),
                stopEpochMillis = now - (oneHour / 3),
                description = "Environmental projects and green innovations combating climate change across Africa.",
                category = "Documentary"
            ),
            EpgProgramme(
                channelId = "dw.news",
                title = "DW News: Global Perspective",
                startEpochMillis = now - (oneHour / 3),
                stopEpochMillis = now + (oneHour * 2 / 3),
                description = "International news reports and in-depth political analysis.",
                category = "News"
            ),
            EpgProgramme(
                channelId = "dw.news",
                title = "Business Africa & Beyond",
                startEpochMillis = now + (oneHour * 2 / 3),
                stopEpochMillis = now + (oneHour * 5 / 3),
                description = "Financial and economic stories from emerging markets.",
                category = "Business"
            ),
            // France 24 (Past Catchup VOD + Live + Upcoming)
            EpgProgramme(
                channelId = "france24.en",
                title = "The Paris Global Debate",
                startEpochMillis = now - (oneHour * 2),
                stopEpochMillis = now - (oneHour * 3 / 4),
                description = "Experts dissect major diplomatic summits and international treaties.",
                category = "Discussion"
            ),
            EpgProgramme(
                channelId = "france24.en",
                title = "Live World News",
                startEpochMillis = now - (oneHour * 3 / 4),
                stopEpochMillis = now + (oneHour / 4),
                description = "Real-time global headlines, field reporting and debates.",
                category = "News"
            ),
            EpgProgramme(
                channelId = "france24.en",
                title = "Focus: European Affairs",
                startEpochMillis = now + (oneHour / 4),
                stopEpochMillis = now + (oneHour * 5 / 4),
                description = "In-depth investigation of current political and cultural trends across Europe.",
                category = "Documentary"
            ),
            // Red Bull TV (Past Catchup VOD + Live + Upcoming)
            EpgProgramme(
                channelId = "redbull.tv",
                title = "Red Bull Rampage: Free Ride Legends",
                startEpochMillis = now - (oneHour * 5 / 2),
                stopEpochMillis = now - (oneHour * 5 / 4),
                description = "The world's greatest mountain bike riders conquer treacherous cliffs in Utah.",
                category = "Sports"
            ),
            EpgProgramme(
                channelId = "redbull.tv",
                title = "Wingsuit Flying Alps Crossing",
                startEpochMillis = now - (oneHour * 5 / 4),
                stopEpochMillis = now - (oneHour / 4),
                description = "Daredevil pilots fly proximity lines through the jagged peaks of the Swiss Alps.",
                category = "Sports"
            ),
            EpgProgramme(
                channelId = "redbull.tv",
                title = "Cliff Diving World Series",
                startEpochMillis = now - (oneHour / 4),
                stopEpochMillis = now + (oneHour * 3 / 4),
                description = "Spectacular high diving action from coastal cliffs around the globe.",
                category = "Sports"
            ),
            EpgProgramme(
                channelId = "redbull.tv",
                title = "Downhill Mountain Biking Cup",
                startEpochMillis = now + (oneHour * 3 / 4),
                stopEpochMillis = now + (oneHour * 7 / 4),
                description = "Adrenaline-fueled downhill downhill racing in the Austrian Alps.",
                category = "Sports"
            ),
            // Movie streams
            EpgProgramme(
                channelId = "bbb.test",
                title = "Big Buck Bunny: The Feature",
                startEpochMillis = now - (oneHour / 4),
                stopEpochMillis = now + (oneHour / 2),
                description = "A large and lovable rabbit deals with bully forest creatures in classic open-source animation.",
                category = "Animation"
            ),
            EpgProgramme(
                channelId = "sintel.test",
                title = "Sintel: Journey of Hope",
                startEpochMillis = now - (oneHour / 2),
                stopEpochMillis = now + (oneHour / 2),
                description = "A lonely young woman searches for a baby dragon she befriended and nursed back to health.",
                category = "Fantasy"
            ),
            EpgProgramme(
                channelId = "tos.test",
                title = "Tears of Steel: Amsterdam 2040",
                startEpochMillis = now - (oneHour / 3),
                stopEpochMillis = now + (oneHour * 2 / 3),
                description = "In a dystopian future, a group of warriors tries to recreate a crucial past moment to save the world.",
                category = "Sci-Fi"
            )
        )

        return EpgData(channels = channels, programmes = programmes)
    }
}

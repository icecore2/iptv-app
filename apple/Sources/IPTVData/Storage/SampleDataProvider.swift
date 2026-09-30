import Foundation
import IPTVCore

/// Provides ready-to-play sample channels, legal test streams, and dynamic live EPG programmes.
public enum SampleDataProvider {

    public static let defaultSamplePlaylistUrl = "https://raw.githubusercontent.com/iptv-org/iptv/master/streams/us.m3u"
    public static let defaultSampleEpgUrl = "https://raw.githubusercontent.com/iptv-org/epg/master/sites/tvguide.com/tvguide.com.epg.xml"

    public static let defaultSamplePair = SavedPlaylistPair(
        id: "sample_demo_profile",
        name: "Demo Channels & EPG",
        playlistUrl: defaultSamplePlaylistUrl,
        epgUrl: defaultSampleEpgUrl,
        isSample: true
    )

    public static func getSamplePlaylist() -> M3uPlaylist {
        let items: [M3uItem] = [
            M3uItem(
                id: "nasa.tv",
                name: "NASA TV Public",
                streamUrl: "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8",
                group: "Science & Tech",
                logoUrl: "https://upload.wikimedia.org/wikipedia/commons/e/e5/NASA_logo.svg",
                tvgId: "nasa.tv",
                tvgName: "NASA TV",
                catchup: "append",
                catchupSource: "?utc=${start}&lutc=${end}",
                catchupDays: 7
            ),
            M3uItem(
                id: "dw.news",
                name: "Deutsche Welle English",
                streamUrl: "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8",
                group: "News",
                logoUrl: "https://upload.wikimedia.org/wikipedia/commons/7/75/Deutsche_Welle_logo.svg",
                tvgId: "dw.news",
                tvgName: "DW English",
                catchup: "append",
                catchupSource: "?utc=${start}&lutc=${end}",
                catchupDays: 7
            ),
            M3uItem(
                id: "france24.en",
                name: "France 24 English",
                streamUrl: "https://static.france24.com/live/F24_EN_LO_HLS/live_tv.m3u8",
                group: "News",
                logoUrl: "https://upload.wikimedia.org/wikipedia/commons/8/82/France_24_logo.svg",
                tvgId: "france24.en",
                tvgName: "France 24",
                catchup: "append",
                catchupSource: "?utc=${start}&lutc=${end}",
                catchupDays: 7
            ),
            M3uItem(
                id: "redbull.tv",
                name: "Red Bull TV",
                streamUrl: "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
                group: "Sports",
                logoUrl: "https://upload.wikimedia.org/wikipedia/en/f/f5/Red_Bull_TV_logo.png",
                tvgId: "redbull.tv",
                tvgName: "Red Bull TV",
                catchup: "append",
                catchupSource: "?utc=${start}&lutc=${end}",
                catchupDays: 7
            ),
            M3uItem(
                id: "bloomberg.quicktake",
                name: "Bloomberg Quicktake",
                streamUrl: "https://bloomberg-quicktake-rakuten.amagi.tv/playlist.m3u8",
                group: "News",
                logoUrl: "https://upload.wikimedia.org/wikipedia/commons/5/52/Bloomberg_Quicktake_logo.svg",
                tvgId: "bloomberg.quicktake",
                tvgName: "Bloomberg Quicktake",
                catchup: "append",
                catchupSource: "?utc=${start}&lutc=${end}",
                catchupDays: 7
            ),
            M3uItem(
                id: "bigbuckbunny",
                name: "Big Buck Bunny (HLS Test)",
                streamUrl: "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                group: "Movies",
                logoUrl: "https://upload.wikimedia.org/wikipedia/commons/c/c5/Big_buck_bunny_poster_big.jpg",
                tvgId: "bbb.test",
                tvgName: "Big Buck Bunny"
            ),
            M3uItem(
                id: "sintel",
                name: "Sintel HD (Animation Test)",
                streamUrl: "https://bitmovin-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
                group: "Movies",
                logoUrl: "https://upload.wikimedia.org/wikipedia/commons/9/90/Sintel_poster.jpg",
                tvgId: "sintel.test",
                tvgName: "Sintel"
            ),
            M3uItem(
                id: "tearsofsteel",
                name: "Tears of Steel (Sci-Fi Test)",
                streamUrl: "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
                group: "Movies",
                logoUrl: "https://upload.wikimedia.org/wikipedia/commons/9/9a/Tears_of_Steel_poster.jpg",
                tvgId: "tos.test",
                tvgName: "Tears of Steel"
            )
        ]

        var groupsSet = [String]()
        var seen = Set<String>()
        for item in items {
            if !seen.contains(item.group) {
                seen.insert(item.group)
                groupsSet.append(item.group)
            }
        }

        return M3uPlaylist(
            items: items,
            groups: groupsSet,
            epgUrl: defaultSampleEpgUrl,
            headerAttributes: ["x-tvg-url": defaultSampleEpgUrl]
        )
    }

    public static func getSampleEpg() -> EpgData {
        let now = Int64(Date().timeIntervalSince1970 * 1000)
        let oneHour: Int64 = 3600_000

        let channels: [String: EpgChannel] = [
            "nasa.tv": EpgChannel(id: "nasa.tv", displayName: "NASA TV", iconUrl: "https://upload.wikimedia.org/wikipedia/commons/e/e5/NASA_logo.svg"),
            "dw.news": EpgChannel(id: "dw.news", displayName: "DW English", iconUrl: "https://upload.wikimedia.org/wikipedia/commons/7/75/Deutsche_Welle_logo.svg"),
            "france24.en": EpgChannel(id: "france24.en", displayName: "France 24", iconUrl: "https://upload.wikimedia.org/wikipedia/commons/8/82/France_24_logo.svg"),
            "redbull.tv": EpgChannel(id: "redbull.tv", displayName: "Red Bull TV", iconUrl: "https://upload.wikimedia.org/wikipedia/en/f/f5/Red_Bull_TV_logo.png"),
            "bbb.test": EpgChannel(id: "bbb.test", displayName: "Big Buck Bunny Cinema", iconUrl: nil),
            "sintel.test": EpgChannel(id: "sintel.test", displayName: "Sintel Animation Theater", iconUrl: nil),
            "tos.test": EpgChannel(id: "tos.test", displayName: "Sci-Fi Showcase", iconUrl: nil)
        ]

        let programmes: [EpgProgramme] = [
            // NASA TV
            EpgProgramme(
                channelId: "nasa.tv",
                title: "Hubble Space Telescope: Deep Universe",
                startEpochMillis: now - (oneHour * 3),
                stopEpochMillis: now - (oneHour * 3 / 2),
                descriptionText: "Spectacular deep space imagery and cosmic discoveries by the Hubble Space Telescope.",
                category: "Science",
                iconUrl: "https://images.unsplash.com/photo-1451187580459-43490279c0fa"
            ),
            EpgProgramme(
                channelId: "nasa.tv",
                title: "Mars Perseverance Rover: Jezero Crater Exploration",
                startEpochMillis: now - (oneHour * 3 / 2),
                stopEpochMillis: now - (oneHour / 2),
                descriptionText: "Astrobiologists analyze soil samples and search for past microbial life on the Red Planet.",
                category: "Science",
                iconUrl: "https://images.unsplash.com/photo-1614728894747-a83421e2b9c9"
            ),
            EpgProgramme(
                channelId: "nasa.tv",
                title: "ISS Space Station Live Feeds",
                startEpochMillis: now - (oneHour / 2),
                stopEpochMillis: now + (oneHour / 2),
                descriptionText: "Live views of Earth from the International Space Station with crew commentary.",
                category: "Science",
                iconUrl: "https://images.unsplash.com/photo-1451187580459-43490279c0fa"
            ),
            EpgProgramme(
                channelId: "nasa.tv",
                title: "Artemis Lunar Mission Briefing",
                startEpochMillis: now + (oneHour / 2),
                stopEpochMillis: now + (oneHour * 2),
                descriptionText: "Deep dive into next-generation lunar exploration technologies.",
                category: "Science"
            ),
            // DW News
            EpgProgramme(
                channelId: "dw.news",
                title: "DW Conflict Zone & Geopolitics",
                startEpochMillis: now - (oneHour * 5 / 2),
                stopEpochMillis: now - (oneHour * 4 / 3),
                descriptionText: "Tough questions and hard talk on world politics and security challenges.",
                category: "News"
            ),
            EpgProgramme(
                channelId: "dw.news",
                title: "Eco Africa: Sustainable Solutions",
                startEpochMillis: now - (oneHour * 4 / 3),
                stopEpochMillis: now - (oneHour / 3),
                descriptionText: "Environmental projects and green innovations combating climate change across Africa.",
                category: "Documentary"
            ),
            EpgProgramme(
                channelId: "dw.news",
                title: "DW News: Global Perspective",
                startEpochMillis: now - (oneHour / 3),
                stopEpochMillis: now + (oneHour * 2 / 3),
                descriptionText: "International news reports and in-depth political analysis.",
                category: "News"
            ),
            EpgProgramme(
                channelId: "dw.news",
                title: "Business Africa & Beyond",
                startEpochMillis: now + (oneHour * 2 / 3),
                stopEpochMillis: now + (oneHour * 5 / 3),
                descriptionText: "Financial and economic stories from emerging markets.",
                category: "Business"
            ),
            // France 24
            EpgProgramme(
                channelId: "france24.en",
                title: "The Paris Global Debate",
                startEpochMillis: now - (oneHour * 2),
                stopEpochMillis: now - (oneHour * 3 / 4),
                descriptionText: "Experts dissect major diplomatic summits and international treaties.",
                category: "Discussion"
            ),
            EpgProgramme(
                channelId: "france24.en",
                title: "Live World News",
                startEpochMillis: now - (oneHour * 3 / 4),
                stopEpochMillis: now + (oneHour / 4),
                descriptionText: "Real-time global headlines, field reporting and debates.",
                category: "News"
            ),
            EpgProgramme(
                channelId: "france24.en",
                title: "Focus: European Affairs",
                startEpochMillis: now + (oneHour / 4),
                stopEpochMillis: now + (oneHour * 5 / 4),
                descriptionText: "In-depth investigation of current political and cultural trends across Europe.",
                category: "Documentary"
            ),
            // Red Bull TV
            EpgProgramme(
                channelId: "redbull.tv",
                title: "Red Bull Rampage: Free Ride Legends",
                startEpochMillis: now - (oneHour * 5 / 2),
                stopEpochMillis: now - (oneHour * 5 / 4),
                descriptionText: "The world's greatest mountain bike riders conquer treacherous cliffs in Utah.",
                category: "Sports"
            ),
            EpgProgramme(
                channelId: "redbull.tv",
                title: "Wingsuit Flying Alps Crossing",
                startEpochMillis: now - (oneHour * 5 / 4),
                stopEpochMillis: now - (oneHour / 4),
                descriptionText: "Daredevil pilots fly proximity lines through the jagged peaks of the Swiss Alps.",
                category: "Sports"
            ),
            EpgProgramme(
                channelId: "redbull.tv",
                title: "Cliff Diving World Series",
                startEpochMillis: now - (oneHour / 4),
                stopEpochMillis: now + (oneHour * 3 / 4),
                descriptionText: "Spectacular high diving action from coastal cliffs around the globe.",
                category: "Sports"
            ),
            EpgProgramme(
                channelId: "redbull.tv",
                title: "Downhill Mountain Biking Cup",
                startEpochMillis: now + (oneHour * 3 / 4),
                stopEpochMillis: now + (oneHour * 7 / 4),
                descriptionText: "Adrenaline-fueled downhill racing in the Austrian Alps.",
                category: "Sports"
            ),
            // Movie streams
            EpgProgramme(
                channelId: "bbb.test",
                title: "Big Buck Bunny: The Feature",
                startEpochMillis: now - (oneHour / 4),
                stopEpochMillis: now + (oneHour / 2),
                descriptionText: "A large and lovable rabbit deals with bully forest creatures in classic open-source animation.",
                category: "Animation"
            ),
            EpgProgramme(
                channelId: "sintel.test",
                title: "Sintel: Journey of Hope",
                startEpochMillis: now - (oneHour / 2),
                stopEpochMillis: now + (oneHour / 2),
                descriptionText: "A lonely young woman searches for a baby dragon she befriended and nursed back to health.",
                category: "Fantasy"
            ),
            EpgProgramme(
                channelId: "tos.test",
                title: "Tears of Steel: Amsterdam 2040",
                startEpochMillis: now - (oneHour / 3),
                stopEpochMillis: now + (oneHour * 2 / 3),
                descriptionText: "In a dystopian future, a group of warriors tries to recreate a crucial past moment to save the world.",
                category: "Sci-Fi"
            )
        ]

        return EpgData(channels: channels, programmes: programmes)
    }
}

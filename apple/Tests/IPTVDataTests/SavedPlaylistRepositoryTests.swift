import XCTest
@testable import IPTVCore
@testable import IPTVData

final class SavedPlaylistRepositoryTests: XCTestCase {

    func testDefaultInitializationHasSamplePair() async {
        let repo = InMemorySavedPlaylistRepository()
        let list = await repo.getSavedPlaylists()

        XCTAssertEqual(list.count, 1)
        let defaultPair = list.first!
        XCTAssertEqual(defaultPair.name, "Demo Channels & EPG")
        XCTAssertTrue(defaultPair.isSample)
        let activeId = await repo.getActivePairId()
        XCTAssertEqual(defaultPair.id, activeId)
    }

    func testSaveAndRetrievePlaylist() async {
        let repo = InMemorySavedPlaylistRepository()
        let newPair = SavedPlaylistPair(
            name: "My Cable IPTV",
            playlistUrl: "http://example.com/playlist.m3u",
            epgUrl: "http://example.com/epg.xml"
        )

        await repo.savePlaylist(newPair)
        let list = await repo.getSavedPlaylists()

        XCTAssertEqual(list.count, 2)
        let saved = list.first(where: { $0.id == newPair.id })
        XCTAssertNotNil(saved)
        XCTAssertEqual(saved?.name, "My Cable IPTV")
        XCTAssertEqual(saved?.playlistUrl, "http://example.com/playlist.m3u")
        XCTAssertEqual(saved?.epgUrl, "http://example.com/epg.xml")
    }

    func testUpdatePlaylist() async {
        let repo = InMemorySavedPlaylistRepository()
        var newPair = SavedPlaylistPair(
            name: "Original Name",
            playlistUrl: "http://example.com/playlist.m3u"
        )
        await repo.savePlaylist(newPair)

        newPair.name = "Updated Name"
        newPair.epgUrl = "http://example.com/epg.xml"
        await repo.updatePlaylist(newPair)

        let list = await repo.getSavedPlaylists()
        let found = list.first(where: { $0.id == newPair.id })
        XCTAssertEqual(found?.name, "Updated Name")
        XCTAssertEqual(found?.epgUrl, "http://example.com/epg.xml")
    }

    func testDeletePlaylistAndClearsActiveIdIfActive() async {
        let repo = InMemorySavedPlaylistRepository()
        let pair = SavedPlaylistPair(
            name: "To Delete",
            playlistUrl: "http://delete.me/list.m3u"
        )
        await repo.savePlaylist(pair)
        await repo.setActivePairId(pair.id)
        let activeBefore = await repo.getActivePairId()
        XCTAssertEqual(pair.id, activeBefore)

        await repo.deletePlaylist(id: pair.id)
        let list = await repo.getSavedPlaylists()
        XCTAssertFalse(list.contains(where: { $0.id == pair.id }))
        let activeAfter = await repo.getActivePairId()
        XCTAssertNil(activeAfter)
    }

    func testUpdateFavoritesPerPlaylist() async {
        let repo = InMemorySavedPlaylistRepository()
        let pair = SavedPlaylistPair(
            name: "Sports",
            playlistUrl: "http://sports.com/list.m3u"
        )
        await repo.savePlaylist(pair)

        await repo.updateFavorites(pairId: pair.id, favoriteIds: ["ch_1", "ch_2"])
        let list = await repo.getSavedPlaylists()
        let saved = list.first(where: { $0.id == pair.id })

        XCTAssertEqual(saved?.favoriteIds, ["ch_1", "ch_2"])
    }
}

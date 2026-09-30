import Foundation
import IPTVCore

/// Repository responsible for loading and parsing M3U playlists from remote URLs or local content.
public final class PlaylistRepository: Sendable {

    private let networkClient: NetworkClient
    private let parser: M3uParser

    public init(
        networkClient: NetworkClient = URLSessionNetworkClient(),
        parser: M3uParser = M3uParser()
    ) {
        self.networkClient = networkClient
        self.parser = parser
    }

    /// Loads and parses an M3U playlist from an HTTP/HTTPS URL.
    public func loadPlaylist(from urlString: String, headers: [String: String] = [:]) async throws -> M3uPlaylist {
        guard let url = URL(string: urlString.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            throw NetworkError.invalidUrl(urlString)
        }
        let data = try await networkClient.fetchData(from: url, headers: headers)
        return parser.parse(data: data)
    }

    /// Parses an M3U playlist directly from a string buffer.
    public func loadPlaylist(from content: String) -> M3uPlaylist {
        return parser.parse(content: content)
    }
}

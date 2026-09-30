import Foundation
import IPTVCore

/// Repository responsible for downloading and streaming XMLTV EPG data (raw XML or Gzip).
public final class EpgRepository: Sendable {

    private let networkClient: NetworkClient
    private let parser: XmlTvParser

    public init(
        networkClient: NetworkClient = URLSessionNetworkClient(),
        parser: XmlTvParser = XmlTvParser()
    ) {
        self.networkClient = networkClient
        self.parser = parser
    }

    /// Loads and parses an XMLTV guide from a remote URL.
    public func loadEpg(from urlString: String, headers: [String: String] = [:]) async throws -> EpgData {
        guard let url = URL(string: urlString.trimmingCharacters(in: .whitespacesAndNewlines)) else {
            throw NetworkError.invalidUrl(urlString)
        }
        let isGzip = urlString.lowercased().hasSuffix(".gz")
        let data = try await networkClient.fetchData(from: url, headers: headers)
        return parser.parse(data: data, isGzip: isGzip ? true : nil)
    }

    /// Parses XMLTV guide directly from an XML string.
    public func loadEpg(from xmlString: String) -> EpgData {
        return parser.parse(xmlString: xmlString)
    }
}

import Foundation

/// Defines network access operations for streaming playlists, EPG XML feeds, and logos.
public protocol NetworkClient: Sendable {
    func fetchData(from url: URL, headers: [String: String]) async throws -> Data
    func postData(to url: URL, body: Data?, headers: [String: String]) async throws -> Data
}

public enum NetworkError: LocalizedError {
    case invalidUrl(String)
    case httpError(statusCode: Int, message: String)
    case emptyResponse

    public var errorDescription: String? {
        switch self {
        case .invalidUrl(let str):
            return "Invalid URL: \(str)"
        case .httpError(let code, let msg):
            return "HTTP Error \(code): \(msg)"
        case .emptyResponse:
            return "Server returned an empty response."
        }
    }
}

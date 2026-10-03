import Foundation

/// High-performance URLSession implementation of NetworkClient.
public final class URLSessionNetworkClient: NetworkClient {

    private let session: URLSession

    public init(session: URLSession? = nil) {
        if let session = session {
            self.session = session
        } else {
            let config = URLSessionConfiguration.default
            config.timeoutIntervalForRequest = 30
            config.timeoutIntervalForResource = 60
            config.httpShouldSetCookies = true
            self.session = URLSession(configuration: config)
        }
    }

    public func fetchData(from url: URL, headers: [String: String] = [:]) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = "GET"

        // Set default User-Agent if not provided
        var hasUserAgent = false
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
            if key.lowercased() == "user-agent" {
                hasUserAgent = true
            }
        }

        if !hasUserAgent {
            request.setValue(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
                forHTTPHeaderField: "User-Agent"
            )
        }

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            return data
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            let message = HTTPURLResponse.localizedString(forStatusCode: httpResponse.statusCode)
            throw NetworkError.httpError(statusCode: httpResponse.statusCode, message: message)
        }

        guard !data.isEmpty else {
            throw NetworkError.emptyResponse
        }

        return data
    }

    public func postData(to url: URL, body: Data? = nil, headers: [String: String] = [:]) async throws -> Data {
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.httpBody = body

        var hasUserAgent = false
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
            if key.lowercased() == "user-agent" {
                hasUserAgent = true
            }
        }

        if !hasUserAgent {
            request.setValue(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15",
                forHTTPHeaderField: "User-Agent"
            )
        }

        let (data, response) = try await session.data(for: request)

        guard let httpResponse = response as? HTTPURLResponse else {
            return data
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            let message = HTTPURLResponse.localizedString(forStatusCode: httpResponse.statusCode)
            throw NetworkError.httpError(statusCode: httpResponse.statusCode, message: message)
        }

        guard !data.isEmpty else {
            throw NetworkError.emptyResponse
        }

        return data
    }
}

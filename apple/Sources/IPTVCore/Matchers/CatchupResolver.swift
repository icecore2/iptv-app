import Foundation

/// Builds catchup timeshift URLs for past broadcast programmes across IPTV formats
/// (Xtream Codes, Flussonic, custom parameter templates, and generic UTC query offsets).
public enum CatchupResolver {

    private static let xcDateFormatter: DateFormatter = {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd:HH-mm"
        formatter.timeZone = TimeZone(secondsFromGMT: 0)
        formatter.locale = Locale(identifier: "en_US_POSIX")
        return formatter
    }()

    private static let xcRegex: NSRegularExpression? = {
        try? NSRegularExpression(
            pattern: #".*/live/([^/]+)/([^/]+)/([^/.]+)(\.[a-zA-Z0-9]+)?"#,
            options: []
        )
    }()

    /// Builds a playable catchup/archive VOD URL for an EPG programme.
    public static func buildVodUrl(
        channel: M3uItem,
        programme: EpgProgramme,
        currentEpochMillis: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) -> String {
        // 1. Direct VOD item (mp4, mkv) where streamUrl is already a static file
        if channel.isVod && !channel.isCatchup {
            let lower = channel.streamUrl.lowercased()
            if lower.hasSuffix(".mp4") || lower.hasSuffix(".mkv") {
                return channel.streamUrl
            }
        }

        let startSec = programme.startEpochMillis / 1000
        let stopSec = programme.stopEpochMillis / 1000
        let durationSec = max(60, stopSec - startSec)
        let offsetSec = max(0, (currentEpochMillis - programme.startEpochMillis) / 1000)
        let nowSec = currentEpochMillis / 1000

        // 2. Custom template in catchupSource
        if let template = channel.catchupSource, !template.trimmingCharacters(in: .whitespaces).isEmpty {
            var resolved = template
                .replacingOccurrences(of: "${start}", with: "\(startSec)")
                .replacingOccurrences(of: "{utc}", with: "\(startSec)")
                .replacingOccurrences(of: "${end}", with: "\(stopSec)")
                .replacingOccurrences(of: "${stop}", with: "\(stopSec)")
                .replacingOccurrences(of: "${lutc}", with: "\(stopSec)")
                .replacingOccurrences(of: "${timestamp}", with: "\(nowSec)")
                .replacingOccurrences(of: "${duration}", with: "\(durationSec)")
                .replacingOccurrences(of: "${offset}", with: "\(offsetSec)")
                .replacingOccurrences(of: "${catchup-id}", with: channel.tvgId ?? channel.id)

            if resolved.lowercased().hasPrefix("http://") || resolved.lowercased().hasPrefix("https://") {
                return resolved
            } else if resolved.hasPrefix("?") {
                let sep = channel.streamUrl.contains("?") ? "&" : "?"
                let base = channel.streamUrl.components(separatedBy: "?").first ?? channel.streamUrl
                let query = String(resolved.dropFirst())
                return "\(base)\(sep)\(query)"
            } else {
                let sep = channel.streamUrl.contains("?") ? "&" : "?"
                return "\(channel.streamUrl)\(sep)\(resolved)"
            }
        }

        // 3. Known catchup types
        if let catchupType = channel.catchup?.lowercased() {
            if catchupType == "flussonic" || catchupType == "fs" {
                if let lastSlash = channel.streamUrl.lastIndex(of: "/") {
                    let baseUrl = String(channel.streamUrl[..<lastSlash])
                    return "\(baseUrl)/timeshift_abs-\(startSec).m3u8"
                }
            } else if catchupType == "xc", let regex = xcRegex {
                let nsRange = NSRange(channel.streamUrl.startIndex..<channel.streamUrl.endIndex, in: channel.streamUrl)
                if let match = regex.firstMatch(in: channel.streamUrl, options: [], range: nsRange),
                   match.numberOfRanges >= 4,
                   let userRange = Range(match.range(at: 1), in: channel.streamUrl),
                   let passRange = Range(match.range(at: 2), in: channel.streamUrl),
                   let streamIdRange = Range(match.range(at: 3), in: channel.streamUrl) {

                    let user = String(channel.streamUrl[userRange])
                    let pass = String(channel.streamUrl[passRange])
                    let streamId = String(channel.streamUrl[streamIdRange])

                    var ext = ".ts"
                    if match.numberOfRanges > 4,
                       match.range(at: 4).location != NSNotFound,
                       let extRange = Range(match.range(at: 4), in: channel.streamUrl) {
                        let parsedExt = String(channel.streamUrl[extRange])
                        if !parsedExt.isEmpty { ext = parsedExt }
                    }

                    let startDate = Date(timeIntervalSince1970: TimeInterval(startSec))
                    let startTimeFormatted = xcDateFormatter.string(from: startDate)
                    let durationMin = durationSec / 60

                    if let liveRange = channel.streamUrl.range(of: "/live/") {
                        let host = String(channel.streamUrl[..<liveRange.lowerBound])
                        return "\(host)/timeshift/\(user)/\(pass)/\(durationMin)/\(startTimeFormatted)/\(streamId)\(ext)"
                    }
                }
            }
        }

        // 4. Default append mode / fallback for IPTV streams
        let sep = channel.streamUrl.contains("?") ? "&" : "?"
        return "\(channel.streamUrl)\(sep)utc=\(startSec)&lutc=\(stopSec)"
    }

    /// Checks if a channel supports catchup VOD or timeshifting.
    public static func hasCatchupSupport(channel: M3uItem) -> Bool {
        return channel.catchup != nil || channel.catchupSource != nil || channel.isVod
    }
}

import Foundation

/// Fast date parser for XMLTV date timestamps (e.g. "20260930120000 +0000", "20260930120000", "20260930120000 Z").
public enum XmlTvDateParser {

    private static let regex: NSRegularExpression? = {
        try? NSRegularExpression(
            pattern: #"^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})(?:\s*([+-]\d{2}:?\d{2}|Z))?"#,
            options: []
        )
    }()

    public static func parseToEpochMillis(_ dateStr: String?) -> Int64? {
        guard let dateStr = dateStr?.trimmingCharacters(in: .whitespacesAndNewlines),
              !dateStr.isEmpty,
              let regex = regex else {
            return nil
        }

        let range = NSRange(dateStr.startIndex..<dateStr.endIndex, in: dateStr)
        guard let match = regex.firstMatch(in: dateStr, options: [], range: range) else {
            return nil
        }

        guard let r1 = Range(match.range(at: 1), in: dateStr),
              let r2 = Range(match.range(at: 2), in: dateStr),
              let r3 = Range(match.range(at: 3), in: dateStr),
              let r4 = Range(match.range(at: 4), in: dateStr),
              let r5 = Range(match.range(at: 5), in: dateStr),
              let r6 = Range(match.range(at: 6), in: dateStr),
              let year = Int(dateStr[r1]),
              let month = Int(dateStr[r2]),
              let day = Int(dateStr[r3]),
              let hour = Int(dateStr[r4]),
              let minute = Int(dateStr[r5]),
              let second = Int(dateStr[r6]) else {
            return nil
        }

        var timeZone = TimeZone(secondsFromGMT: 0) ?? .gmt
        if match.numberOfRanges > 7, match.range(at: 7).location != NSNotFound,
           let r7 = Range(match.range(at: 7), in: dateStr) {
            let tzStr = String(dateStr[r7])
            timeZone = parseTimeZone(tzStr)
        }

        var components = DateComponents()
        components.calendar = Calendar(identifier: .gregorian)
        components.timeZone = timeZone
        components.year = year
        components.month = month
        components.day = day
        components.hour = hour
        components.minute = minute
        components.second = second

        guard let date = components.date else { return nil }
        return Int64(date.timeIntervalSince1970 * 1000)
    }

    private static func parseTimeZone(_ tzStr: String) -> TimeZone {
        if tzStr.isEmpty || tzStr == "Z" {
            return TimeZone(secondsFromGMT: 0) ?? .gmt
        }
        let clean = tzStr.replacingOccurrences(of: ":", with: "")
        if clean.count == 5 {
            // e.g. "+0200" or "-0500"
            let sign = clean.hasPrefix("-") ? -1 : 1
            let startIndex = clean.index(clean.startIndex, offsetBy: 1)
            let midIndex = clean.index(startIndex, offsetBy: 2)
            let hourStr = String(clean[startIndex..<midIndex])
            let minStr = String(clean[midIndex...])
            if let h = Int(hourStr), let m = Int(minStr) {
                let seconds = sign * ((h * 3600) + (m * 60))
                return TimeZone(secondsFromGMT: seconds) ?? .gmt
            }
        }
        return TimeZone(secondsFromGMT: 0) ?? .gmt
    }
}

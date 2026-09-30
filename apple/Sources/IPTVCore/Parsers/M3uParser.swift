import Foundation

/// Parses M3U / M3U8 playlists containing IPTV streams, category groups, channel attributes, and EPG URLs.
public final class M3uParser: Sendable {

    private static let attributeRegex: NSRegularExpression? = {
        try? NSRegularExpression(
            pattern: #"([a-zA-Z0-9_-]+)=(?:"([^"]*)"|'([^']*)'|([^ \t\r\n,]+))"#,
            options: []
        )
    }()

    public init() {}

    /// Parses an M3U playlist from a string.
    public func parse(content: String) -> M3uPlaylist {
        var items: [M3uItem] = []
        var groupsSet: [String] = []
        var seenGroups = Set<String>()
        var headerAttributes: [String: String] = [:]
        var epgUrl: String?

        var currentTvgId: String?
        var currentTvgName: String?
        var currentTvgLogo: String?
        var currentGroup: String?
        var currentName: String?
        var currentIsRadio = false
        var currentCatchup: String?
        var currentCatchupSource: String?
        var currentCatchupDays: Int?
        var currentHeaders: [String: String] = [:]
        var hasPendingItem = false

        content.enumerateLines { line, _ in
            let trimmed = line.trimmingCharacters(in: .whitespacesAndNewlines)
            if trimmed.isEmpty { return }

            if trimmed.lowercased().hasPrefix("#extm3u") {
                let attrsPart = String(trimmed.dropFirst(7))
                let attrs = Self.parseAttributes(attrsPart)
                for (k, v) in attrs {
                    headerAttributes[k] = v
                }
                epgUrl = attrs["url-tvg"] ?? attrs["x-tvg-url"] ?? attrs["tvg-url"]
            } else if trimmed.lowercased().hasPrefix("#extinf:") {
                currentHeaders.removeAll()
                hasPendingItem = true

                let afterExtInf = String(trimmed.dropFirst(8)).trimmingCharacters(in: .whitespaces)
                let commaIndex = Self.findChannelTitleSeparator(afterExtInf)

                let attrsPart: String
                let titlePart: String
                if commaIndex != -1 {
                    let splitIdx = afterExtInf.index(afterExtInf.startIndex, offsetBy: commaIndex)
                    attrsPart = String(afterExtInf[..<splitIdx]).trimmingCharacters(in: .whitespaces)
                    let nextIdx = afterExtInf.index(after: splitIdx)
                    titlePart = String(afterExtInf[nextIdx...]).trimmingCharacters(in: .whitespaces)
                } else {
                    attrsPart = afterExtInf
                    titlePart = ""
                }

                let attrs = Self.parseAttributes(attrsPart)
                currentTvgId = attrs["tvg-id"]
                currentTvgName = attrs["tvg-name"]
                currentTvgLogo = attrs["tvg-logo"]
                currentGroup = attrs["group-title"]
                currentIsRadio = attrs["radio"]?.lowercased() == "true"
                currentCatchup = attrs["catchup"] ?? attrs["catchup-type"] ?? headerAttributes["catchup"] ?? headerAttributes["catchup-type"]
                currentCatchupSource = attrs["catchup-source"] ?? headerAttributes["catchup-source"]
                let rawDays = attrs["catchup-days"] ?? headerAttributes["catchup-days"]
                currentCatchupDays = rawDays.flatMap { Int($0) }

                if !titlePart.isEmpty {
                    currentName = titlePart
                } else if let tvgName = currentTvgName, !tvgName.isEmpty {
                    currentName = tvgName
                } else {
                    currentName = "Unnamed Channel"
                }
            } else if trimmed.lowercased().hasPrefix("#extgrp:") {
                currentGroup = String(trimmed.dropFirst(8)).trimmingCharacters(in: .whitespaces)
            } else if trimmed.lowercased().hasPrefix("#extvlcopt:") {
                let opt = String(trimmed.dropFirst(11)).trimmingCharacters(in: .whitespaces)
                Self.parseVlcOption(opt, into: &currentHeaders)
            } else if !trimmed.hasPrefix("#") {
                // Stream URL line
                if hasPendingItem {
                    let groupName = (currentGroup?.trimmingCharacters(in: .whitespaces).isEmpty == false) ? currentGroup! : "General"
                    if !seenGroups.contains(groupName) {
                        seenGroups.insert(groupName)
                        groupsSet.append(groupName)
                    }

                    let item = M3uItem(
                        id: currentTvgId ?? UUID().uuidString,
                        name: currentName ?? "Channel",
                        streamUrl: trimmed,
                        group: groupName,
                        logoUrl: currentTvgLogo,
                        tvgId: currentTvgId,
                        tvgName: currentTvgName,
                        headers: currentHeaders,
                        isRadio: currentIsRadio,
                        catchup: currentCatchup,
                        catchupSource: currentCatchupSource,
                        catchupDays: currentCatchupDays
                    )
                    items.append(item)
                    hasPendingItem = false
                    currentHeaders.removeAll()
                }
            }
        }

        return M3uPlaylist(
            items: items,
            groups: groupsSet,
            epgUrl: epgUrl,
            headerAttributes: headerAttributes
        )
    }

    /// Parses an M3U playlist from raw UTF-8 data.
    public func parse(data: Data) -> M3uPlaylist {
        let content = String(data: data, encoding: .utf8) ?? String(decoding: data, as: UTF8.self)
        return parse(content: content)
    }

    private static func findChannelTitleSeparator(_ text: String) -> Int {
        var inQuotes = false
        var quoteChar: Character = " "
        var index = 0

        for char in text {
            if (char == "\"" || char == "'") {
                if !inQuotes {
                    inQuotes = true
                    quoteChar = char
                } else if char == quoteChar {
                    inQuotes = false
                }
            } else if char == "," && !inQuotes {
                return index
            }
            index += 1
        }
        return -1
    }

    private static func parseAttributes(_ text: String) -> [String: String] {
        var map: [String: String] = [:]
        guard let regex = attributeRegex else { return map }

        let nsRange = NSRange(text.startIndex..<text.endIndex, in: text)
        let matches = regex.matches(in: text, options: [], range: nsRange)

        for match in matches {
            guard match.numberOfRanges > 1,
                  let kRange = Range(match.range(at: 1), in: text) else { continue }
            let key = String(text[kRange]).lowercased()

            var value = ""
            for idx in 2...4 {
                if match.numberOfRanges > idx,
                   match.range(at: idx).location != NSNotFound,
                   let vRange = Range(match.range(at: idx), in: text) {
                    value = String(text[vRange])
                    break
                }
            }
            map[key] = value
        }
        return map
    }

    private static func parseVlcOption(_ opt: String, into headers: inout [String: String]) {
        guard let equalsIdx = opt.firstIndex(of: "=") else { return }
        let key = String(opt[..<equalsIdx]).trimmingCharacters(in: .whitespaces).lowercased()
        let value = String(opt[opt.index(after: equalsIdx)...]).trimmingCharacters(in: .whitespaces)

        switch key {
        case "http-user-agent":
            headers["User-Agent"] = value
        case "http-referrer", "http-referer":
            headers["Referer"] = value
        default:
            break
        }
    }
}

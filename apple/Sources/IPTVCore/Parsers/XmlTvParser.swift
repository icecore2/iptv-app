import Foundation
#if canImport(zlib)
import zlib
#endif

/// High-performance streaming XMLTV parser supporting both raw XML and Gzip compressed streams.
public final class XmlTvParser: NSObject, XMLParserDelegate, Sendable {

    public override init() {
        super.init()
    }

    /// Parses XMLTV data into EpgData containing channels and schedule programmes.
    public func parse(data: Data, isGzip: Bool? = nil) -> EpgData {
        let decompressed = decompressIfGzip(data, explicitGzip: isGzip)
        let parserDelegate = XmlTvParserDelegate()
        let xmlParser = XMLParser(data: decompressed)
        xmlParser.delegate = parserDelegate
        xmlParser.shouldProcessNamespaces = false
        xmlParser.shouldReportNamespacePrefixes = false
        xmlParser.parse()
        return parserDelegate.epgData
    }

    /// Parses XMLTV XML from a string.
    public func parse(xmlString: String) -> EpgData {
        guard let data = xmlString.data(using: .utf8) else {
            return EpgData()
        }
        return parse(data: data, isGzip: false)
    }

    private func decompressIfGzip(_ data: Data, explicitGzip: Bool?) -> Data {
        let isGzip = explicitGzip ?? Self.isGzipData(data)
        if !isGzip {
            return data
        }
        return Self.gunzip(data) ?? data
    }

    public static func isGzipData(_ data: Data) -> Bool {
        guard data.count >= 2 else { return false }
        return data[0] == 0x1f && data[1] == 0x8b
    }

    /// Decompresses gzip data using standard zlib inflate with Gzip window bits (16 + MAX_WBITS).
    public static func gunzip(_ data: Data) -> Data? {
        guard data.count >= 2 else { return nil }
        #if canImport(zlib)
        var stream = z_stream()
        var status = inflateInit2_(&stream, 16 + MAX_WBITS, ZLIB_VERSION, Int32(MemoryLayout<z_stream>.size))
        guard status == Z_OK else { return nil }
        defer { inflateEnd(&stream) }

        var decompressed = Data(capacity: data.count * 4)
        let chunkSize = 32768
        var buffer = [UInt8](repeating: 0, count: chunkSize)

        data.withUnsafeBytes { rawBufferPointer in
            stream.next_in = UnsafeMutablePointer<Bytef>(mutating: rawBufferPointer.bindMemory(to: Bytef.self).baseAddress)
            stream.avail_in = uInt(data.count)

            repeat {
                buffer.withUnsafeMutableBytes { outBufferPointer in
                    stream.next_out = outBufferPointer.bindMemory(to: Bytef.self).baseAddress
                    stream.avail_out = uInt(chunkSize)
                    status = inflate(&stream, Z_NO_FLUSH)
                }

                let bytesProduced = chunkSize - Int(stream.avail_out)
                if bytesProduced > 0 {
                    decompressed.append(buffer, count: bytesProduced)
                }
            } while status == Z_OK
        }

        return (status == Z_STREAM_END || status == Z_OK) ? decompressed : nil
        #else
        return nil
        #endif
    }
}

/// Internal SAX parsing delegate for streaming XMLTV traversal.
final class XmlTvParserDelegate: NSObject, XMLParserDelegate {
    var channels: [String: EpgChannel] = [:]
    var programmes: [EpgProgramme] = []

    var epgData: EpgData {
        EpgData(channels: channels, programmes: programmes)
    }

    private var currentElement: String = ""
    private var textAccumulator: String = ""

    // Current Channel state
    private var inChannel = false
    private var currentChannelId: String?
    private var currentChannelDisplayName: String = ""
    private var currentChannelIconUrl: String?

    // Current Programme state
    private var inProgramme = false
    private var currentProgrammeChannelId: String?
    private var currentProgrammeStart: Int64 = 0
    private var currentProgrammeStop: Int64 = 0
    private var currentProgrammeTitle: String = ""
    private var currentProgrammeDesc: String?
    private var currentProgrammeCategory: String?
    private var currentProgrammeIconUrl: String?

    func parser(
        _ parser: XMLParser,
        didStartElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?,
        attributes attributeDict: [String: String] = [:]
    ) {
        let tag = elementName.lowercased()
        currentElement = tag
        textAccumulator = ""

        if tag == "channel" {
            inChannel = true
            currentChannelId = attributeDict["id"]
            currentChannelDisplayName = ""
            currentChannelIconUrl = nil
        } else if tag == "programme" {
            inProgramme = true
            currentProgrammeChannelId = attributeDict["channel"]
            let startStr = attributeDict["start"]
            let stopStr = attributeDict["stop"]
            let startEpoch = XmlTvDateParser.parseToEpochMillis(startStr) ?? 0
            let stopEpoch = XmlTvDateParser.parseToEpochMillis(stopStr) ?? (startEpoch + 3600_000)
            currentProgrammeStart = startEpoch
            currentProgrammeStop = stopEpoch
            currentProgrammeTitle = ""
            currentProgrammeDesc = nil
            currentProgrammeCategory = nil
            currentProgrammeIconUrl = nil
        } else if tag == "icon" {
            let src = attributeDict["src"]
            if inChannel {
                currentChannelIconUrl = src
            } else if inProgramme {
                currentProgrammeIconUrl = src
            }
        }
    }

    func parser(_ parser: XMLParser, foundCharacters string: String) {
        textAccumulator.append(string)
    }

    func parser(
        _ parser: XMLParser,
        didEndElement elementName: String,
        namespaceURI: String?,
        qualifiedName qName: String?
    ) {
        let tag = elementName.lowercased()
        let trimmedText = textAccumulator.trimmingCharacters(in: .whitespacesAndNewlines)

        if inChannel {
            if tag == "display-name" {
                currentChannelDisplayName = trimmedText
            } else if tag == "channel" {
                if let id = currentChannelId {
                    let name = currentChannelDisplayName.isEmpty ? id : currentChannelDisplayName
                    channels[id] = EpgChannel(id: id, displayName: name, iconUrl: currentChannelIconUrl)
                }
                inChannel = false
            }
        } else if inProgramme {
            if tag == "title" {
                currentProgrammeTitle = trimmedText
            } else if tag == "desc" {
                currentProgrammeDesc = trimmedText.isEmpty ? nil : trimmedText
            } else if tag == "category" {
                currentProgrammeCategory = trimmedText.isEmpty ? nil : trimmedText
            } else if tag == "programme" {
                if let channelId = currentProgrammeChannelId {
                    let title = currentProgrammeTitle.isEmpty ? "No Title" : currentProgrammeTitle
                    let prog = EpgProgramme(
                        channelId: channelId,
                        title: title,
                        startEpochMillis: currentProgrammeStart,
                        stopEpochMillis: currentProgrammeStop,
                        descriptionText: currentProgrammeDesc,
                        category: currentProgrammeCategory,
                        iconUrl: currentProgrammeIconUrl
                    )
                    programmes.append(prog)
                }
                inProgramme = false
            }
        }
    }
}

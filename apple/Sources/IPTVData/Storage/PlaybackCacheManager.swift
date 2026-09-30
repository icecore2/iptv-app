import Foundation

/// Manages media disk buffering, time-shift cache directory, and storage quota management.
public final class PlaybackCacheManager: @unchecked Sendable {

    public static let shared = PlaybackCacheManager()
    private let cacheDirName = "iptv_stream_buffer"

    private let fileManager = FileManager.default
    private let lock = NSLock()

    private init() {
        createCacheDirectoryIfNeeded()
    }

    /// URL to the stream buffer and time-shift disk directory.
    public var cacheDirectoryUrl: URL {
        let base = fileManager.urls(for: .cachesDirectory, in: .userDomainMask).first ?? URL(fileURLWithPath: NSTemporaryDirectory())
        return base.appendingPathComponent(cacheDirName, isDirectory: true)
    }

    private func createCacheDirectoryIfNeeded() {
        lock.lock()
        defer { lock.unlock() }
        let url = cacheDirectoryUrl
        if !fileManager.fileExists(atPath: url.path) {
            try? fileManager.createDirectory(at: url, withIntermediateDirectories: true)
        }
    }

    /// Queries the total bytes currently occupied by cached media segments and buffers.
    public func getUsedStorageBytes() -> Int64 {
        lock.lock()
        defer { lock.unlock() }
        let dir = cacheDirectoryUrl
        guard let enumerator = fileManager.enumerator(at: dir, includingPropertiesForKeys: [.fileSizeKey, .isDirectoryKey]) else {
            return 0
        }

        var totalSize: Int64 = 0
        for case let fileUrl as URL in enumerator {
            guard let resourceValues = try? fileUrl.resourceValues(forKeys: [.fileSizeKey, .isDirectoryKey]),
                  resourceValues.isDirectory == false,
                  let size = resourceValues.fileSize else {
                continue
            }
            totalSize += Int64(size)
        }
        return totalSize
    }

    /// Queries available free disk space on the system volume.
    public func getFreeDiskSpaceBytes() -> Int64 {
        let path = cacheDirectoryUrl.path
        guard let attributes = try? fileManager.attributesOfFileSystem(forPath: path),
              let freeSize = attributes[.systemFreeSize] as? NSNumber else {
            return 0
        }
        return freeSize.int64Value
    }

    /// Clears all temporary stream buffer segments and resets disk storage usage to 0.
    public func clearCache() {
        lock.lock()
        defer { lock.unlock() }
        let dir = cacheDirectoryUrl
        if fileManager.fileExists(atPath: dir.path) {
            try? fileManager.removeItem(at: dir)
            try? fileManager.createDirectory(at: dir, withIntermediateDirectories: true)
        }
    }
}

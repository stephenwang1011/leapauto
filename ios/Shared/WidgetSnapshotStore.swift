import Foundation
import LeapCore

/// Compile in both targets. The extension only calls read(); it has no credential
/// access, network client, or command dispatch. Main-app writes are atomic.
struct WidgetSnapshotStore {
    static let appGroupIdentifier = "group.com.leapauto.ios"
    static let widgetKind = "LeapAutoStatusWidget"
    private let fileManager: FileManager

    init(fileManager: FileManager = .default) { self.fileManager = fileManager }

    private func snapshotURL() throws -> URL {
        guard let container = fileManager.containerURL(forSecurityApplicationGroupIdentifier: Self.appGroupIdentifier) else {
            throw StorageError.groupUnavailable
        }
        return container.appendingPathComponent("LeapAutoWidget", isDirectory: true)
            .appendingPathComponent("snapshot-v1.json", isDirectory: false)
    }

    func read() -> WidgetSnapshotRead {
        do {
            let handle = try FileHandle(forReadingFrom: snapshotURL())
            defer { try? handle.close() }
            // Bound the read itself, not just decoding after allocating a whole file.
            let data = try handle.read(upToCount: WidgetSnapshotCodec.maximumBytes + 1) ?? Data()
            return .available(try WidgetSnapshotCodec.decode(data))
        } catch {
            let nsError = error as NSError
            if Self.isMissingFile(nsError) { return .missing }
            return .unavailable
        }
    }

    func save(_ snapshot: WidgetSnapshot) throws {
        let data = try WidgetSnapshotCodec.encode(snapshot)
        let file = try snapshotURL()
        let directory = file.deletingLastPathComponent()
        try fileManager.createDirectory(at: directory, withIntermediateDirectories: true,
                                        attributes: [.protectionKey: FileProtectionType.complete])
        try fileManager.setAttributes([.protectionKey: FileProtectionType.complete], ofItemAtPath: directory.path)
        var excludedDirectory = directory
        var resourceValues = URLResourceValues()
        resourceValues.isExcludedFromBackup = true
        try excludedDirectory.setResourceValues(resourceValues)
        try data.write(to: file, options: [.atomic, .completeFileProtection])
    }

    /// Replace the whole payload with a non-sensitive tombstone. If the protected
    /// write fails, deletion is a second safe route; report failure if both fail.
    func clear() throws {
        do { try save(.unconfigured()) }
        catch {
            do { try fileManager.removeItem(at: snapshotURL()) }
            catch {
                let nsError = error as NSError
                guard Self.isMissingFile(nsError) else {
                    throw StorageError.clearFailed
                }
            }
        }
    }

    private static func isMissingFile(_ error: NSError) -> Bool {
        error.domain == NSCocoaErrorDomain
            && [NSFileNoSuchFileError, NSFileReadNoSuchFileError].contains(error.code)
    }

    enum StorageError: Error { case groupUnavailable, clearFailed }
}

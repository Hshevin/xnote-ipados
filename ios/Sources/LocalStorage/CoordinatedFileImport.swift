import Foundation

public enum CoordinatedFileImport {
    /// Copies while holding the provider's read coordination and security scope.
    /// Returned staging files belong to the caller and must be removed after admission or failure.
    public static func stage(source: URL, directory: URL) async throws -> URL {
        try Task.checkCancellation()
        let scoped = source.startAccessingSecurityScopedResource()
        defer { if scoped { source.stopAccessingSecurityScopedResource() } }
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let destination = directory.appendingPathComponent("import-\(UUID().uuidString).pdf")
        var coordinationError: NSError?
        var copyError: Error?
        NSFileCoordinator().coordinate(readingItemAt: source, options: .withoutChanges, error: &coordinationError) { coordinated in
            do {
                guard FileManager.default.createFile(atPath: destination.path, contents: nil) else { throw CocoaError(.fileWriteUnknown) }
                let input = try FileHandle(forReadingFrom: coordinated)
                defer { try? input.close() }
                let output = try FileHandle(forWritingTo: destination)
                defer { try? output.close() }
                while true {
                    try Task.checkCancellation()
                    let data = try input.read(upToCount: 65_536) ?? Data()
                    if data.isEmpty { break }
                    try output.write(contentsOf: data)
                }
                try output.synchronize()
            } catch { copyError = error }
        }
        do {
            if let coordinationError { throw coordinationError }
            if let copyError { throw copyError }
            try Task.checkCancellation()
            return destination
        } catch {
            try? FileManager.default.removeItem(at: destination)
            throw error
        }
    }
}

import Foundation
import CryptoKit
import Darwin

public enum PDFStoreError: Error, Equatable {
    case invalidIdentifier, missingObject, hashMismatch, corruptIndex, invalidOwner
}

/// App-wide store. The application owns a single instance per private directory.
/// Files are immutable after admission; only explicit hash-verified relinking can replace damage.
public actor PDFObjectStore {
    public struct Lease: Sendable {
        public let token: UUID
        public let resourceID: String
        public let url: URL
    }
    private struct Index: Codable {
        var version = 1
        var objects: [String: Date] = [:]
        var references: [String: Set<String>] = [:]
    }
    private let directory: URL
    private let indexURL: URL
    private var index: Index
    private var leases: [UUID: String] = [:]
    private let grace: TimeInterval

    public init(directory: URL, grace: TimeInterval = 7 * 24 * 60 * 60) throws {
        precondition(grace >= 0)
        self.directory = directory
        self.indexURL = directory.appendingPathComponent("index.json")
        self.grace = grace
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        if FileManager.default.fileExists(atPath: indexURL.path) {
            do {
                let loaded = try JSONDecoder().decode(Index.self, from: Data(contentsOf: indexURL))
                guard loaded.version == 1,
                      loaded.objects.keys.allSatisfy(Self.valid),
                      loaded.references.keys.allSatisfy({ !$0.isEmpty }),
                      loaded.references.values.allSatisfy({ $0.allSatisfy { loaded.objects[$0] != nil } }) else {
                    throw PDFStoreError.corruptIndex
                }
                index = loaded
            } catch { throw PDFStoreError.corruptIndex }
        } else {
            // Never recreate an empty index over existing objects: recovery must be explicit.
            let existing = try FileManager.default.contentsOfDirectory(at: directory, includingPropertiesForKeys: nil)
            guard !existing.contains(where: { $0.pathExtension == "pdf" }) else { throw PDFStoreError.corruptIndex }
            index = Index()
            try Self.publish(try JSONEncoder().encode(index), to: indexURL)
        }
    }

    public func admit(source: URL, expectedID: String? = nil, now: Date = Date()) throws -> String {
        try importObject(source: source, expectedID: expectedID, repairing: false, now: now)
    }

    /// Explicit relinking requires the recorded digest, even if the old object is missing.
    public func restore(source: URL, expectedID: String, now: Date = Date()) throws -> String {
        try importObject(source: source, expectedID: expectedID, repairing: true, now: now)
    }

    private func importObject(source: URL, expectedID: String?, repairing: Bool, now: Date) throws -> String {
        if let expectedID, !Self.valid(expectedID) { throw PDFStoreError.invalidIdentifier }
        let staged = directory.appendingPathComponent("import-\(UUID().uuidString).tmp")
        defer { try? FileManager.default.removeItem(at: staged) }
        guard FileManager.default.createFile(atPath: staged.path, contents: nil) else { throw CocoaError(.fileWriteUnknown) }
        let input = try FileHandle(forReadingFrom: source)
        defer { try? input.close() }
        let output = try FileHandle(forWritingTo: staged)
        defer { try? output.close() }
        var digest = SHA256()
        while true {
            try Task.checkCancellation()
            let chunk = try input.read(upToCount: 65_536) ?? Data()
            if chunk.isEmpty { break }
            digest.update(data: chunk)
            try output.write(contentsOf: chunk)
        }
        try output.synchronize()
        try output.close()
        let id = Self.hex(digest.finalize())
        if let expectedID, expectedID != id { throw PDFStoreError.hashMismatch }
        let destination = objectURL(id)
        if FileManager.default.fileExists(atPath: destination.path) {
            if (try? Self.hash(destination)) != id {
                guard repairing else { throw PDFStoreError.hashMismatch }
                try Self.rename(staged, destination)
            }
        } else {
            try Self.rename(staged, destination)
        }
        // If index publication fails, the verified object is retained, never rolled back/deleted.
        var next = index
        if next.objects[id] == nil { next.objects[id] = now }
        try commit(next)
        return id
    }

    public func pin(owner: String, ids: Set<String>, now: Date = Date()) throws {
        guard !owner.isEmpty else { throw PDFStoreError.invalidOwner }
        for id in ids { _ = try verifiedURL(id) }
        var next = index
        let old = next.references[owner] ?? []
        next.references[owner] = ids.isEmpty ? nil : ids
        let allPinned = Set(next.references.values.flatMap { $0 })
        for id in old.subtracting(allPinned) { next.objects[id] = now }
        try commit(next)
    }

    public func references(owner: String) -> Set<String> { index.references[owner] ?? [] }

    public func acquire(_ id: String) throws -> Lease {
        let url = try verifiedURL(id)
        let token = UUID()
        leases[token] = id
        return Lease(token: token, resourceID: id, url: url)
    }

    public func release(_ lease: Lease, now: Date = Date()) throws {
        guard leases[lease.token] == lease.resourceID else { return }
        // Start grace before releasing the last protection; failed persistence retains the lease.
        var next = index
        next.objects[lease.resourceID] = now
        try commit(next)
        leases.removeValue(forKey: lease.token)
    }

    @discardableResult
    public func collect(now: Date = Date()) throws -> Int {
        let protected = Set(index.references.values.flatMap { $0 }).union(leases.values)
        let candidates = index.objects.filter { !protected.contains($0.key) && now.timeIntervalSince($0.value) >= grace }
        var next = index
        var removed = 0
        for (id, _) in candidates {
            try Task.checkCancellation()
            let url = objectURL(id)
            if FileManager.default.fileExists(atPath: url.path) { try FileManager.default.removeItem(at: url) }
            next.objects.removeValue(forKey: id)
            removed += 1
        }
        try commit(next)
        return removed
    }

    private func verifiedURL(_ id: String) throws -> URL {
        guard Self.valid(id) else { throw PDFStoreError.invalidIdentifier }
        let url = objectURL(id)
        guard index.objects[id] != nil, FileManager.default.fileExists(atPath: url.path) else { throw PDFStoreError.missingObject }
        guard try Self.hash(url) == id else { throw PDFStoreError.hashMismatch }
        return url
    }
    private func objectURL(_ id: String) -> URL { directory.appendingPathComponent(id + ".pdf") }
    private func commit(_ next: Index) throws {
        try Self.publish(try JSONEncoder().encode(next), to: indexURL)
        index = next
    }
    private static func valid(_ id: String) -> Bool {
        id.utf8.count == 64 && id.utf8.allSatisfy { (48...57).contains($0) || (97...102).contains($0) }
    }
    private static func hex(_ digest: SHA256.Digest) -> String { digest.map { String(format: "%02x", $0) }.joined() }
    private static func hash(_ url: URL) throws -> String {
        let input = try FileHandle(forReadingFrom: url)
        defer { try? input.close() }
        var digest = SHA256()
        while true {
            try Task.checkCancellation()
            let chunk = try input.read(upToCount: 65_536) ?? Data()
            if chunk.isEmpty { break }
            digest.update(data: chunk)
        }
        return hex(digest.finalize())
    }
    private static func publish(_ data: Data, to destination: URL) throws {
        let staged = destination.deletingLastPathComponent().appendingPathComponent("index-\(UUID().uuidString).tmp")
        defer { try? FileManager.default.removeItem(at: staged) }
        try data.write(to: staged, options: .withoutOverwriting)
        let handle = try FileHandle(forWritingTo: staged)
        defer { try? handle.close() }
        try handle.synchronize()
        try rename(staged, destination)
    }
    private static func rename(_ source: URL, _ destination: URL) throws {
        let result = source.withUnsafeFileSystemRepresentation { from in
            destination.withUnsafeFileSystemRepresentation { to in Darwin.rename(from!, to!) }
        }
        guard result == 0 else { throw NSError(domain: NSPOSIXErrorDomain, code: Int(errno)) }
    }
}

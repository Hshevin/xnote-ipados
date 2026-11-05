import Foundation

@main
struct StorageMain {
    static func main() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("xnotes-store-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let source = root.appendingPathComponent("source.pdf")
        let bytes = Data("%PDF-1.4\nobject store byte preservation probe\n%%EOF".utf8)
        try bytes.write(to: source)
        let store = try PDFObjectStore(directory: root.appendingPathComponent("objects"), grace: 0)
        let id = try await store.admit(source: source)
        let second = try await store.admit(source: source)
        precondition(id == second)
        try await store.pin(owner: "note", ids: [id])
        let lease = try await store.acquire(id)
        let loaded = try Data(contentsOf: lease.url)
        precondition(loaded == bytes)
        try await store.pin(owner: "note", ids: [])
        let protected = try await store.collect()
        precondition(protected == 0)
        try await store.release(lease)
        let collected = try await store.collect()
        precondition(collected == 1)
        let original = try Data(contentsOf: source)
        precondition(original == bytes)
        print("PASS: iOS PDF object deduplication, byte preservation, lease and collection")
    }
}

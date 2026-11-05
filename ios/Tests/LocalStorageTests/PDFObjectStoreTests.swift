import XCTest
@testable import LocalStorage

final class PDFObjectStoreTests: XCTestCase, @unchecked Sendable {
    private func fixture() throws -> (URL, URL, URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let source = root.appendingPathComponent("source.pdf")
        try Data("%PDF-1.4\nimmutable original\n%%EOF".utf8).write(to: source)
        return (root, source, root.appendingPathComponent("objects"))
    }
    func testDeduplicationAndReferencesSurviveReopen() async throws {
        let (root, source, directory) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try PDFObjectStore(directory: directory)
        let id = try await store.admit(source: source)
        let again = try await store.admit(source: source)
        XCTAssertEqual(id, again)
        try await store.pin(owner: "document-1", ids: [id])
        let reopened = try PDFObjectStore(directory: directory)
        let refs = await reopened.references(owner: "document-1")
        XCTAssertEqual(refs, [id])
        let lease = try await reopened.acquire(id)
        XCTAssertEqual(try Data(contentsOf: lease.url), try Data(contentsOf: source))
        try await reopened.release(lease)
    }
    func testLeaseAndReferencePreventCollectionThenGraceApplies() async throws {
        let (root, source, directory) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try PDFObjectStore(directory: directory, grace: 10)
        let start = Date(timeIntervalSince1970: 100)
        let id = try await store.admit(source: source, now: start)
        try await store.pin(owner: "note", ids: [id], now: start)
        var removed = try await store.collect(now: start.addingTimeInterval(100))
        XCTAssertEqual(removed, 0)
        let lease = try await store.acquire(id)
        try await store.pin(owner: "note", ids: [], now: start)
        removed = try await store.collect(now: start.addingTimeInterval(100))
        XCTAssertEqual(removed, 0)
        try await store.release(lease, now: start.addingTimeInterval(100))
        removed = try await store.collect(now: start.addingTimeInterval(109))
        XCTAssertEqual(removed, 0)
        removed = try await store.collect(now: start.addingTimeInterval(110))
        XCTAssertEqual(removed, 1)
        XCTAssertTrue(FileManager.default.fileExists(atPath: source.path))
    }
    func testRelinkRejectsWrongBytesAndRepairsOnlyExpectedHash() async throws {
        let (root, source, directory) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try PDFObjectStore(directory: directory)
        let id = try await store.admit(source: source)
        let object = directory.appendingPathComponent(id + ".pdf")
        let wrong = root.appendingPathComponent("wrong.pdf")
        try Data("wrong".utf8).write(to: wrong)
        do { _ = try await store.restore(source: wrong, expectedID: id); XCTFail("accepted wrong PDF") }
        catch { XCTAssertEqual(error as? PDFStoreError, .hashMismatch) }
        XCTAssertEqual(try Data(contentsOf: object), try Data(contentsOf: source))
        try Data("damage".utf8).write(to: object)
        do { _ = try await store.acquire(id); XCTFail("accepted corruption") }
        catch { XCTAssertEqual(error as? PDFStoreError, .hashMismatch) }
        _ = try await store.restore(source: source, expectedID: id)
        XCTAssertEqual(try Data(contentsOf: object), try Data(contentsOf: source))
    }
    func testCorruptIndexIsPreservedAndNeverRebuiltAsEmpty() async throws {
        let (root, source, directory) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try PDFObjectStore(directory: directory)
        _ = try await store.admit(source: source)
        let index = directory.appendingPathComponent("index.json")
        let damaged = Data("broken".utf8)
        try damaged.write(to: index)
        XCTAssertThrowsError(try PDFObjectStore(directory: directory))
        XCTAssertEqual(try Data(contentsOf: index), damaged)
        try FileManager.default.removeItem(at: index)
        XCTAssertThrowsError(try PDFObjectStore(directory: directory))
    }
    func testFailedIndexPublicationRetainsObjectAndInMemoryReferences() async throws {
        let (root, source, directory) = try fixture()
        defer { try? FileManager.default.removeItem(at: root) }
        let store = try PDFObjectStore(directory: directory)
        let id = try await store.admit(source: source)
        try await store.pin(owner: "note", ids: [id])
        let index = directory.appendingPathComponent("index.json")
        let backup = try Data(contentsOf: index)
        try FileManager.default.removeItem(at: index)
        try FileManager.default.createDirectory(at: index, withIntermediateDirectories: false)
        do { try await store.pin(owner: "note", ids: []); XCTFail("publication should fail") } catch {}
        let refs = await store.references(owner: "note")
        XCTAssertEqual(refs, [id])
        XCTAssertTrue(FileManager.default.fileExists(atPath: directory.appendingPathComponent(id + ".pdf").path))
        try FileManager.default.removeItem(at: index)
        try backup.write(to: index)
        _ = try PDFObjectStore(directory: directory)
    }
}

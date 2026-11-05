import XCTest
@testable import LocalStorage

final class CoordinatedFileImportTests: XCTestCase, @unchecked Sendable {
    func testCoordinatedCopyPreservesSourceAndUsesUniqueStagingFiles() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let source = root.appendingPathComponent("source.pdf")
        let bytes = Data(repeating: 42, count: 150_000)
        try bytes.write(to: source)
        let directory = root.appendingPathComponent("staging")
        let first = try await CoordinatedFileImport.stage(source: source, directory: directory)
        let second = try await CoordinatedFileImport.stage(source: source, directory: directory)
        XCTAssertNotEqual(first, second)
        XCTAssertEqual(try Data(contentsOf: first), bytes)
        XCTAssertEqual(try Data(contentsOf: source), bytes)
    }
    func testReadFailureLeavesNoPartialSnapshot() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let directory = root.appendingPathComponent("staging")
        do {
            _ = try await CoordinatedFileImport.stage(source: root.appendingPathComponent("missing.pdf"), directory: directory)
            XCTFail("missing source accepted")
        } catch {}
        XCTAssertEqual(try FileManager.default.contentsOfDirectory(atPath: directory.path), [])
    }
    func testCancelledTaskDoesNotCreateSnapshot() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        let task = Task {
            withUnsafeCurrentTask { $0?.cancel() }
            return try await CoordinatedFileImport.stage(source: root.appendingPathComponent("source.pdf"), directory: root)
        }
        do { _ = try await task.value; XCTFail("cancelled import succeeded") }
        catch { XCTAssertTrue(error is CancellationError) }
        XCTAssertFalse(FileManager.default.fileExists(atPath: root.path))
    }
}

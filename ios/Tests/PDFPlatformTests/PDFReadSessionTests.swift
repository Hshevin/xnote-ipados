import XCTest
import PDFKit
import CoreGraphics
import LocalStorage
@testable import PDFPlatform

@MainActor
final class PDFReadSessionTests: XCTestCase {
    private func fixture(rotation: Int) throws -> (URL, URL) {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let source = root.appendingPathComponent("source.pdf")
        var media = CGRect(x: 0, y: 0, width: 300, height: 400)
        let context = CGContext(source as CFURL, mediaBox: &media, nil)!
        context.beginPDFPage(nil)
        context.setFillColor(CGColor(gray: 0, alpha: 1))
        context.fill(CGRect(x: 40, y: 60, width: 50, height: 70))
        context.endPDFPage(); context.closePDF()
        // Fixture authoring only. The production session never exposes these mutation APIs.
        let pdf = PDFDocument(url: source)!
        pdf.page(at: 0)!.setBounds(CGRect(x: 20, y: 30, width: 200, height: 300), for: .cropBox)
        pdf.page(at: 0)!.rotation = rotation
        XCTAssertTrue(pdf.write(to: source))
        return (root, source)
    }
    func testRotationCropTransformsMatchAndroidCoordinateContract() async throws {
        for rotation in [0, 90, 180, 270] {
            let (root, source) = try fixture(rotation: rotation)
            defer { try? FileManager.default.removeItem(at: root) }
            let store = try PDFObjectStore(directory: root.appendingPathComponent("objects"))
            let id = try await store.admit(source: source)
            let session = try await PDFReadSession.open(store: store, id: id)
            let info = try session.info(page: 0)
            XCTAssertEqual(info.cropBox, CGRect(x: 20, y: 30, width: 200, height: 300))
            XCTAssertEqual(info.displaySize, rotation % 180 == 0 ? CGSize(width: 200, height: 300) : CGSize(width: 300, height: 200))
            let point = CGPoint(x: 60, y: 100)
            let expected: CGPoint
            switch rotation {
            case 90: expected = CGPoint(x: 70, y: 40)
            case 180: expected = CGPoint(x: 160, y: 70)
            case 270: expected = CGPoint(x: 230, y: 160)
            default: expected = CGPoint(x: 40, y: 230)
            }
            let displayed = point.applying(try session.pageToDisplay(page: 0))
            XCTAssertEqual(displayed.x, expected.x, accuracy: 0.001)
            XCTAssertEqual(displayed.y, expected.y, accuracy: 0.001)
            let back = displayed.applying(try session.displayToPage(page: 0))
            XCTAssertEqual(back.x, point.x, accuracy: 0.001)
            XCTAssertEqual(back.y, point.y, accuracy: 0.001)
            try await session.close()
        }
    }
    func testPreviewAndClosePreserveBytesAndLease() async throws {
        let (root, source) = try fixture(rotation: 90)
        defer { try? FileManager.default.removeItem(at: root) }
        let bytes = try Data(contentsOf: source)
        let store = try PDFObjectStore(directory: root.appendingPathComponent("objects"), grace: 0)
        let id = try await store.admit(source: source)
        let session = try await PDFReadSession.open(store: store, id: id)
        XCTAssertEqual(try session.pageCount, 1)
        let image = try session.preview(page: 0, width: 300, height: 200)
XCTAssertGreaterThan(image.width, 0)
let pixels = CGContext(data: nil, width: 64, height: 64, bitsPerComponent: 8, bytesPerRow: 64,
                       space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue)!
pixels.draw(image, in: CGRect(x: 0, y: 0, width: 64, height: 64))
let values = UnsafeBufferPointer(start: pixels.data!.assumingMemoryBound(to: UInt8.self), count: 64 * 64)
XCTAssertLessThan(values.min()!, 128)
XCTAssertGreaterThan(values.max()!, 240)

        XCTAssertThrowsError(try session.preview(page: 0, width: Int.max, height: 2))
        XCTAssertThrowsError(try session.info(page: 1))
        let protected = try await store.collect()
        XCTAssertEqual(protected, 0)
        let lease = try await store.acquire(id)
        XCTAssertEqual(try Data(contentsOf: lease.url), bytes)
        try await store.release(lease)
        try await session.close()
        XCTAssertThrowsError(try session.info(page: 0))
        let removed = try await store.collect()
        XCTAssertEqual(removed, 1)
        XCTAssertEqual(try Data(contentsOf: source), bytes)
    }
    func testInvalidPDFReleasesLease() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        defer { try? FileManager.default.removeItem(at: root) }
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        let source = root.appendingPathComponent("invalid.pdf")
        try Data("invalid".utf8).write(to: source)
        let store = try PDFObjectStore(directory: root.appendingPathComponent("objects"), grace: 0)
        let id = try await store.admit(source: source)
        do { _ = try await PDFReadSession.open(store: store, id: id); XCTFail("invalid PDF accepted") } catch {}
        let removed = try await store.collect()
        XCTAssertEqual(removed, 1)
    }
}

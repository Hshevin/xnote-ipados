import Foundation
import CoreGraphics
import CoreText
import PDFKit
import PDFPlatform
import LocalStorage

@main
struct PDFMain {
    @MainActor
    static func main() async throws {
        let root = FileManager.default.temporaryDirectory.appendingPathComponent("xnotes-pdf-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: root, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: root) }
        let source = root.appendingPathComponent("source.pdf")
        var media = CGRect(x: 0, y: 0, width: 300, height: 400)
        let context = CGContext(source as CFURL, mediaBox: &media, nil)!
        for _ in 0..<4 {
            context.beginPDFPage(nil)
            context.setFillColor(CGColor(gray: 0, alpha: 1))
            context.fill(CGRect(x: 40, y: 60, width: 50, height: 20))
            context.textPosition = CGPoint(x: 40, y: 120)
            let line = NSAttributedString(string: "XNotes PDF probe", attributes: [NSAttributedString.Key(kCTFontAttributeName as String): CTFontCreateWithName("Helvetica" as CFString, 16, nil)])
            CTLineDraw(CTLineCreateWithAttributedString(line), context)
            context.endPDFPage()
        }
        context.closePDF()
        let authoring = PDFDocument(url: source)!
        for index in 0..<4 {
            let page = authoring.page(at: index)!
            page.rotation = index * 90
            page.setBounds(CGRect(x: 20, y: 30, width: 200, height: 300), for: .cropBox)
        }
        precondition(authoring.write(to: source))
        let original = try Data(contentsOf: source)
        let store = try PDFObjectStore(directory: root.appendingPathComponent("objects"), grace: 0)
        let id = try await store.admit(source: source)
        let session = try await PDFReadSession.open(store: store, id: id)
        let count = try session.pageCount
        precondition(count == 4)
        for index in 0..<4 {
            let metadata = try session.info(page: index)
            precondition(metadata.rotation == index * 90)
            let p = CGPoint(x: 60, y: 100)
            let displayed = p.applying(try session.pageToDisplay(page: index))
            let back = displayed.applying(try session.displayToPage(page: index))
            precondition(abs(back.x - p.x) < 0.001 && abs(back.y - p.y) < 0.001)
            let text = try session.text(page: index)
            precondition(text.contains("XNotes PDF probe"))
            let bounds = try session.textBounds(page: index, userSpacePoint: CGPoint(x: 50, y: 125))
            precondition(bounds != nil && !bounds!.isEmpty)
            let image = try session.preview(page: index, width: 300, height: 300)
            precondition(image.width * image.height <= PDFReadSession.maximumPreviewPixels)
        }
        let protected = try await store.collect()
        precondition(protected == 0)
        let lease = try await store.acquire(id)
        let retained = try Data(contentsOf: lease.url)
        precondition(retained == original)
        try await store.release(lease)
        try await session.close()
        let removed = try await store.collect()
        precondition(removed == 1)
        print("PASS: iOS PDFKit rotations, crop coordinates, text hit, previews, bytes and leases")
    }
}

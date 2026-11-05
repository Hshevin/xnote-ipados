import Foundation
import CoreGraphics
import PDFKit
import LocalStorage
#if canImport(UIKit)
import UIKit
#else
import AppKit
#endif

public enum PDFReadError: Error { case invalidDocument, lockedDocument, closed, invalidPage, invalidSize, renderFailed }

/// Private PDFKit state never escapes this read-only boundary. No mutation or write APIs are exposed.
@MainActor
public final class PDFReadSession {
    public struct PageInfo {
        public let mediaBox: CGRect
        public let cropBox: CGRect
        public let rotation: Int
        public let displaySize: CGSize
    }
    private var document: PDFDocument?
    private var lease: PDFObjectStore.Lease?
    private let store: PDFObjectStore
    public static let maximumPreviewPixels = 4_194_304

    private init(document: PDFDocument, lease: PDFObjectStore.Lease, store: PDFObjectStore) {
        self.document = document; self.lease = lease; self.store = store
    }
    public static func open(store: PDFObjectStore, id: String) async throws -> PDFReadSession {
        let lease = try await store.acquire(id)
        do {
            try Task.checkCancellation()
            guard let document = PDFDocument(url: lease.url) else { throw PDFReadError.invalidDocument }
            guard !document.isLocked else { throw PDFReadError.lockedDocument }
            guard document.pageCount > 0 else { throw PDFReadError.invalidDocument }
            return PDFReadSession(document: document, lease: lease, store: store)
        } catch {
            try await store.release(lease)
            throw error
        }
    }
    deinit {
        if let lease {
            let store = store
            Task { try? await store.release(lease) }
        }
    }
    public func close() async throws {
        document = nil
        if let lease {
            try await store.release(lease)
            self.lease = nil
        }
    }
    public var pageCount: Int { get throws {
        guard let document else { throw PDFReadError.closed }
        return document.pageCount
    } }
    private func page(_ index: Int) throws -> PDFPage {
        guard let document else { throw PDFReadError.closed }
        guard index >= 0, index < document.pageCount, let page = document.page(at: index) else { throw PDFReadError.invalidPage }
        return page
    }
    public func info(page index: Int) throws -> PageInfo {
        let page = try page(index)
        let crop = page.bounds(for: .cropBox)
        let rotation = ((page.rotation % 360) + 360) % 360
        guard crop.width > 0, crop.height > 0, crop.width.isFinite, crop.height.isFinite else { throw PDFReadError.invalidSize }
        let rotated = rotation == 90 || rotation == 270
        return PageInfo(mediaBox: page.bounds(for: .mediaBox), cropBox: crop, rotation: rotation,
                        displaySize: rotated ? CGSize(width: crop.height, height: crop.width) : crop.size)
    }
    /// PDF user space → displayed page points (top-left origin, inherent rotation applied).
    /// CoreGraphics supplies the transform; the editor subsequently applies its viewport transform.
    public func pageToDisplay(page index: Int) throws -> CGAffineTransform {
        let metadata = try info(page: index)
        guard let ref = try page(index).pageRef else { throw PDFReadError.invalidPage }
        let destination = CGRect(origin: .zero, size: metadata.displaySize)
        let draw = ref.getDrawingTransform(.cropBox, rect: destination, rotate: 0, preserveAspectRatio: true)
        return draw.concatenating(CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: 0, ty: destination.height))
    }
    public func displayToPage(page index: Int) throws -> CGAffineTransform {
        try pageToDisplay(page: index).inverted()
    }
    /// A bounded preview, including source PDF annotations. This is not the tiled editing renderer.
    public func preview(page index: Int, width: Int, height: Int) throws -> CGImage {
        guard width > 0, height > 0, width <= Self.maximumPreviewPixels / height else { throw PDFReadError.invalidSize }
        try Task.checkCancellation()
        let page = try page(index)
        let thumbnail = page.thumbnail(of: CGSize(width: width, height: height), for: .cropBox)
        let image: CGImage?
        #if canImport(UIKit)
        image = thumbnail.cgImage
        #else
        image = thumbnail.cgImage(forProposedRect: nil, context: nil, hints: nil)
        #endif
        try Task.checkCancellation()
        guard let image, image.width > 0, image.height > 0,
              image.width <= Self.maximumPreviewPixels / image.height else { throw PDFReadError.renderFailed }
        return image
    }
    public func text(page index: Int) throws -> String { try page(index).string ?? "" }
    public func textBounds(page index: Int, userSpacePoint: CGPoint) throws -> CGRect? {
        try page(index).selectionForWord(at: userSpacePoint)?.bounds(for: page(index))
    }
}

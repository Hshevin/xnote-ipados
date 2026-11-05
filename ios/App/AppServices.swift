import Foundation
import LocalStorage

/// One store actor per private object directory across windows and probe screens.
@MainActor
final class AppServices {
    static let shared = AppServices()
    private var pdfObjects: PDFObjectStore?
    private init() {}
    func pdfStore() throws -> PDFObjectStore {
        if let pdfObjects { return pdfObjects }
        let support = try FileManager.default.url(for: .applicationSupportDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
        let created = try PDFObjectStore(directory: support.appendingPathComponent("PDFObjects"))
        pdfObjects = created
        return created
    }
}

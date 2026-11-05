import SwiftUI
import PDFKit
import PDFPlatform
import LocalStorage
import UniformTypeIdentifiers

@MainActor
final class PDFPreviewState: ObservableObject {
    @Published private(set) var image: CGImage?
    @Published private(set) var page = 0
    @Published private(set) var count = 0
    @Published private(set) var busy = false
    @Published var errorKey: String?
    private var session: PDFReadSession?

    func open(_ source: URL) async {
        guard !busy else { return }
        busy = true
        defer { busy = false }
        var candidate: PDFReadSession?
        do {
            let cache = try FileManager.default.url(for: .cachesDirectory, in: .userDomainMask, appropriateFor: nil, create: true)
            let staged = try await CoordinatedFileImport.stage(source: source, directory: cache.appendingPathComponent("PDFImports"))
            defer { try? FileManager.default.removeItem(at: staged) }
            guard let validation = PDFDocument(url: staged) else { throw PDFReadError.invalidDocument }
            guard !validation.isLocked else { throw PDFReadError.lockedDocument }
            guard validation.pageCount > 0 else { throw PDFReadError.invalidDocument }
            let store = try AppServices.shared.pdfStore()
            let id = try await store.admit(source: staged)
            let opened = try await PDFReadSession.open(store: store, id: id)
            candidate = opened
            let first = try opened.preview(page: 0, width: 1024, height: 1024)
            let pages = try opened.pageCount
            try Task.checkCancellation()
            let previous = session
            session = opened
            candidate = nil
            image = first; page = 0; count = pages; errorKey = nil
            if let previous {
                do { try await previous.close() } catch { errorKey = "pdf.cleanup" }
            }
        } catch {
            if let candidate { try? await candidate.close() }
            if !(error is CancellationError) {
                if case PDFReadError.lockedDocument = error { errorKey = "pdf.locked" }
                else { errorKey = "pdf.failed" }
            }
        }
    }
    func move(_ delta: Int) {
        guard !busy, let session, (0..<count).contains(page + delta) else { return }
        do {
            let next = page + delta
            let rendered = try session.preview(page: next, width: 1024, height: 1024)
            image = rendered; page = next
        } catch { errorKey = "pdf.failed" }
    }
}

struct PDFPreviewScreen: View {
    @StateObject private var state = PDFPreviewState()
    @State private var selecting = false
    @State private var acceptingSelection = false
    @State private var importTask: Task<Void, Never>?
    @Environment(\.colorScheme) private var scheme
    var body: some View {
        VStack(alignment: .leading, spacing: Space.s16) {
            Text("pdf.title").font(.system(size: TextSize.heading))
            Text("pdf.description").font(.system(size: TextSize.body))
            HStack(spacing: Space.s16) {
                Button("pdf.open") { selecting = true }.disabled(state.busy)
                Button("pdf.previous") { state.move(-1) }.disabled(state.busy || state.page == 0)
                Text(state.count == 0 ? "—" : "\(state.page + 1) / \(state.count)")
                    .accessibilityLabel(Text("pdf.page"))
                Button("pdf.next") { state.move(1) }.disabled(state.busy || state.page + 1 >= state.count)
                if state.busy { ProgressView().tint(scheme == .dark ? .white : .black) }
            }.buttonStyle(ProbeButtonStyle())
            if let image = state.image {
                GeometryReader { proxy in
                    Image(decorative: image, scale: 1).resizable().scaledToFit()
                        .frame(width: proxy.size.width, height: proxy.size.height)
                        .accessibilityLabel(Text("pdf.preview"))
                }
            } else {
                Spacer()
                Text("pdf.empty").font(.system(size: TextSize.body))
                Spacer()
            }
        }
        .padding(Space.s16)
        .foregroundColor(scheme == .dark ? .white : .black)
        .background(scheme == .dark ? Color.black : Color.white)
        .fileImporter(isPresented: $selecting, allowedContentTypes: [.pdf]) { result in
            guard acceptingSelection else { return }
            switch result {
            case .success(let url): importTask = Task { await state.open(url) }
            case .failure(let error):
                if (error as NSError).code != NSUserCancelledError { state.errorKey = "pdf.failed" }
            }
        }
        .onAppear { acceptingSelection = true }
        .onDisappear { acceptingSelection = false; importTask?.cancel() }
        .alert(Text("pdf.error"), isPresented: Binding(get: { state.errorKey != nil }, set: { if !$0 { state.errorKey = nil } })) {
            Button("common.ok") { state.errorKey = nil }
        } message: { Text(LocalizedStringKey(state.errorKey ?? "pdf.failed")) }
    }
}

struct ProbeButtonStyle: ButtonStyle {
    var selected = false
    @Environment(\.colorScheme) private var scheme
    @Environment(\.isEnabled) private var enabled
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.system(size: TextSize.body))
            .frame(minWidth: minTouchTarget, minHeight: minTouchTarget)
            .padding(.horizontal, Space.s8)
            .foregroundColor(enabled ? ((scheme == .dark) != (selected || configuration.isPressed) ? .white : .black) : .gray)
            .background(selected || configuration.isPressed ? (scheme == .dark ? Color.white : Color.black) : Color.clear)
    }
}

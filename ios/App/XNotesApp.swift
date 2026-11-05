import SwiftUI

@main
struct XNotesApp: App {
    var body: some Scene {
        WindowGroup { InputProbeScreen() }
    }
}

@MainActor
final class InputProbeState: ObservableObject {
    @Published var actual = 0
    @Published var predicted = 0
    @Published var corrections = 0
    @Published var cancellations = 0
    func receive(_ event: PencilCaptureView.Event) {
        switch event {
        case .began: actual += 1
        case let .moved(_, points, predictions): actual += points.count; predicted = predictions.count
        case let .ended(_, points): actual += points.count; predicted = 0
        case .cancelled: cancellations += 1; predicted = 0
        case let .corrected(_, points): corrections += points.count
        }
    }
}

struct InputProbeScreen: View {
    @StateObject private var state = InputProbeState()
    @Environment(\.colorScheme) private var scheme
    @Environment(\.scenePhase) private var phase
    var body: some View {
        VStack(alignment: .leading, spacing: Space.s16) {
            Text("probe.title").font(.system(size: TextSize.heading))
            Text("probe.description").font(.system(size: TextSize.body))
            HStack(spacing: Space.s16) {
                metric("probe.actual", state.actual)
                metric("probe.predicted", state.predicted)
                metric("probe.corrections", state.corrections)
                metric("probe.cancellations", state.cancellations)
            }
            CaptureSurface(state: state, enabled: phase == .active)
                .border(scheme == .dark ? Color.white : Color.black)
                .accessibilityLabel(Text("probe.surface"))
        }
        .padding(Space.s16)
        .foregroundColor(scheme == .dark ? .white : .black)
        .background(scheme == .dark ? Color.black : Color.white)
    }
    private func metric(_ key: LocalizedStringKey, _ value: Int) -> some View {
        VStack(alignment: .leading, spacing: Space.s8) {
            Text(key)
            Text(value.formatted())
        }.font(.system(size: TextSize.body)).frame(minHeight: minTouchTarget)
    }
}

struct CaptureSurface: UIViewRepresentable {
    let state: InputProbeState
    let enabled: Bool
    func makeUIView(context: Context) -> PencilCaptureView {
        let view = PencilCaptureView()
        view.receive = { [weak state] event in state?.receive(event) }
        return view
    }
    func updateUIView(_ view: PencilCaptureView, context: Context) {
        if !enabled { view.invalidate() }
        view.isUserInteractionEnabled = enabled
    }
    static func dismantleUIView(_ view: PencilCaptureView, coordinator: ()) {
        view.invalidate(); view.receive = nil
    }
}

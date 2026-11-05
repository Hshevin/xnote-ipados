import UIKit

/// P0 capture boundary. Raw axis values remain intact; predictions have a separate channel.
@MainActor
final class PencilCaptureView: UIView {
    struct Sample {
        let timestamp: TimeInterval
        let location: CGPoint
        let force: CGFloat
        let maximumForce: CGFloat
        let altitude: CGFloat
        let azimuth: CGFloat
        let roll: CGFloat?
        let estimationIndex: Int?
        let estimated: UITouch.Properties
        let awaitingUpdate: UITouch.Properties
    }
    enum Event {
        case began(UUID, Sample)
        case moved(UUID, actual: [Sample], predicted: [Sample])
        case ended(UUID, [Sample])
        case cancelled(UUID)
        case corrected(UUID, [Sample])
    }
    var receive: ((Event) -> Void)?
    private var identities: [ObjectIdentifier: UUID] = [:]
    private var active: Set<ObjectIdentifier> = []
    private var estimates: [Int: UUID] = [:]

    override init(frame: CGRect) {
        super.init(frame: frame)
        isMultipleTouchEnabled = true
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) is not supported") }

    private func sample(_ touch: UITouch) -> Sample {
        let roll: CGFloat?
        if #available(iOS 17.5, *) { roll = touch.rollAngle } else { roll = nil }
        return Sample(timestamp: touch.timestamp, location: touch.preciseLocation(in: self),
                      force: touch.force, maximumForce: touch.maximumPossibleForce,
                      altitude: touch.altitudeAngle, azimuth: touch.azimuthAngle(in: self), roll: roll,
                      estimationIndex: touch.estimationUpdateIndex?.intValue,
                      estimated: touch.estimatedProperties,
                      awaitingUpdate: touch.estimatedPropertiesExpectingUpdates)
    }
private func capture(_ touches: [UITouch], session: UUID) -> [Sample] {
    touches.map { touch in
        if let index = touch.estimationUpdateIndex?.intValue,
           !touch.estimatedPropertiesExpectingUpdates.isEmpty {
            estimates[index] = session
        }
        return sample(touch)
    }
}
    override func touchesBegan(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches where touch.type == .pencil {
            let key = ObjectIdentifier(touch)
            let id = UUID()
            identities[key] = id; active.insert(key)
            receive?(.began(id, capture([touch], session: id)[0]))
        }
    }
    override func touchesMoved(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            let key = ObjectIdentifier(touch)
            guard active.contains(key), let id = identities[key] else { continue }
            receive?(.moved(id, actual: capture(event?.coalescedTouches(for: touch) ?? [touch], session: id),
                           predicted: (event?.predictedTouches(for: touch) ?? []).map(sample)))
        }
    }
    override func touchesEnded(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            let key = ObjectIdentifier(touch)
            guard active.remove(key) != nil, let id = identities[key] else { continue }
            receive?(.ended(id, capture(event?.coalescedTouches(for: touch) ?? [touch], session: id)))
            identities.removeValue(forKey: key)
        }
    }
    override func touchesCancelled(_ touches: Set<UITouch>, with event: UIEvent?) {
        for touch in touches {
            let key = ObjectIdentifier(touch)
            guard let id = identities.removeValue(forKey: key) else { continue }
            active.remove(key); estimates = estimates.filter { $0.value != id }
            receive?(.cancelled(id))
        }
    }
override func touchesEstimatedPropertiesUpdated(_ touches: Set<UITouch>) {
    for touch in touches {
        guard let index = touch.estimationUpdateIndex?.intValue,
              let id = estimates[index] else { continue }
        receive?(.corrected(id, [sample(touch)]))
        if touch.estimatedPropertiesExpectingUpdates.isEmpty {
            estimates.removeValue(forKey: index)
        }
    }
}
    /// Called by document close/replacement and scene deactivation.
    func invalidate() {
        for key in active {
            if let id = identities[key] { receive?(.cancelled(id)) }
        }
        active.removeAll(); identities.removeAll(); estimates.removeAll()
    }
    override func didMoveToWindow() {
        super.didMoveToWindow()
        if window == nil { invalidate() }
    }
}

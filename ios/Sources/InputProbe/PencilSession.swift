/// Platform-neutral input probe. This is not the document model or a persistence format.
public struct PencilSample: Equatable, Sendable {
    public let sequence: Int
    public let x: Double
    public let y: Double
    public let pressure: Double
    public init(sequence: Int, x: Double, y: Double, pressure: Double) {
        self.sequence = sequence; self.x = x; self.y = y; self.pressure = pressure
    }
}

public struct PencilSnapshot: Equatable, Sendable {
    public let revision: Int
    public let samples: [PencilSample]
}

/// One contact identity owns each session. Predictions never enter committed snapshots.
public struct PencilSession: Sendable {
    public enum Phase: Sendable { case idle, contact, ended, invalidated }
    public private(set) var phase: Phase = .idle
    public private(set) var predictions: [PencilSample] = []
    public private(set) var revision = 0
    private var owner: Int?
    private var samples: [PencilSample] = []
    public init() {}
    public mutating func begin(identity: Int) -> Bool {
        guard phase == .idle else { return false }
        owner = identity; phase = .contact; return true
    }
    public mutating func append(identity: Int, actual: [PencilSample], predicted: [PencilSample]) {
        guard phase == .contact, owner == identity else { return }
        for sample in actual where sample.sequence > (samples.last?.sequence ?? -1) {
            samples.append(sample)
        }
        predictions = predicted
    }
    public mutating func end(identity: Int) -> PencilSnapshot? {
        guard phase == .contact, owner == identity else { return nil }
        phase = .ended; predictions = []; revision += 1
        return snapshot
    }
    public mutating func cancel(identity: Int) {
        guard owner == identity, phase == .contact else { return }
        invalidate()
    }
    /// Deletion, undo, or closing the document invalidates late callbacks permanently.
    public mutating func invalidate() {
        phase = .invalidated; owner = nil; samples = []; predictions = []
    }
    public mutating func correct(identity: Int, sample: PencilSample) -> PencilSnapshot? {
        guard owner == identity, phase == .contact || phase == .ended,
              let index = samples.firstIndex(where: { $0.sequence == sample.sequence }) else { return nil }
        samples[index] = sample
        revision += 1
        return phase == .ended ? snapshot : nil
    }
    public var snapshot: PencilSnapshot { PencilSnapshot(revision: revision, samples: samples) }
}

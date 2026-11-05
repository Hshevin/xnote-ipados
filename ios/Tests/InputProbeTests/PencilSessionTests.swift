import XCTest
@testable import InputProbe

final class PencilSessionTests: XCTestCase {
    func sample(_ sequence: Int, _ pressure: Double = 0.5) -> PencilSample {
        PencilSample(sequence: sequence, x: Double(sequence), y: 0, pressure: pressure)
    }
    func testPredictionReplacementAndInputIdentity() {
        var session = PencilSession()
        XCTAssertTrue(session.begin(identity: 1))
        session.append(identity: 1, actual: [sample(0)], predicted: [sample(1), sample(2)])
        session.cancel(identity: 2)
        session.append(identity: 1, actual: [sample(0), sample(1)], predicted: [sample(2)])
        XCTAssertEqual(session.predictions.count, 1)
        let saved = session.end(identity: 1)
        XCTAssertEqual(saved?.samples, [sample(0), sample(1)])
        XCTAssertTrue(session.predictions.isEmpty)
    }
    func testLateCorrectionDoesNotMutateFrozenSaveOrReviveDeletion() {
        var session = PencilSession()
        _ = session.begin(identity: 1)
        session.append(identity: 1, actual: [sample(0)], predicted: [])
        let saved = session.end(identity: 1)!
        let corrected = session.correct(identity: 1, sample: sample(0, 1))!
        XCTAssertEqual(saved.samples[0].pressure, 0.5)
        XCTAssertEqual(corrected.samples[0].pressure, 1)
        XCTAssertGreaterThan(corrected.revision, saved.revision)
        session.invalidate()
        XCTAssertNil(session.correct(identity: 1, sample: sample(0)))
        XCTAssertFalse(session.begin(identity: 1))
    }
    func testCancellationDiscardsAllContactData() {
        var session = PencilSession()
        _ = session.begin(identity: 1)
        session.append(identity: 1, actual: [sample(0)], predicted: [sample(1)])
        session.cancel(identity: 1)
        XCTAssertTrue(session.snapshot.samples.isEmpty)
        XCTAssertNil(session.end(identity: 1))
        XCTAssertNil(session.correct(identity: 1, sample: sample(0)))
    }
}

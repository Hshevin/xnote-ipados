import XNotesCoreProbe

/// Compiled against the generated Objective-C/Swift boundary. Execute on iOS in the next gate.
func verifyGeometryInterop() -> Bool {
    let probe = GeometryProbe()
    let batch = KotlinDoubleArray(size: 6)
    for (index, value) in [0.0, 0.0, 3.0, 4.0, 6.0, 8.0].enumerated() {
        batch.set(index: Int32(index), value: value)
    }
    return probe.distance(x0: 0, y0: 0, x1: 3, y1: 4) == 5
        && probe.pathLength(coordinates: batch) == 10
}

func verifySampleCodecInterop() -> Bool {
    let input = "[[1.25,-2.5,0.125,12],[3,4,1,15]]"
    return (try? SampleCodecProbe().roundTrip(json: input)) == input
}

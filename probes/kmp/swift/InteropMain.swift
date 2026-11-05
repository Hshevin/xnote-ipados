import XNotesCoreProbe

@main
struct InteropMain {
    static func main() throws {
        precondition(verifyGeometryInterop())
precondition(verifySampleCodecInterop())
let source = "[[1.25,-2.5,0.125,12],[3,4,1,15]]"
let moved = try SampleCodecProbe().translate(json: source, dx: 10, dy: -5)
precondition(moved == "[[11.25,-7.5,0.125,12],[13,-1,1,15]]")
let restored = try SampleCodecProbe().translate(json: moved, dx: -10, dy: 5)
precondition(restored == source)

        do {
            _ = try SampleCodecProbe().roundTrip(json: "[] []")
            fatalError("Malformed input was accepted")
        } catch {
            print("PASS: Swift/Kotlin geometry, sample codec, and error boundary")
        }
    }
}

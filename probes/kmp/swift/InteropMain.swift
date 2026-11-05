import XNotesCoreProbe

@main
struct InteropMain {
    static func main() throws {
        precondition(verifyGeometryInterop())
        precondition(verifySampleCodecInterop())
        do {
            _ = try SampleCodecProbe().roundTrip(json: "[] []")
            fatalError("Malformed input was accepted")
        } catch {
            print("PASS: Swift/Kotlin geometry, sample codec, and error boundary")
        }
    }
}

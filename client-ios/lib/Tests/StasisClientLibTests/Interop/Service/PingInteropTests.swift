import Foundation
@testable import StasisClientLib
import Testing

@Suite("Ping interop")
struct PingInteropTests {
    @Test("decode and re-encode a ping")
    func ping() throws {
        try assert(
            domain: "service",
            resource: "Ping",
            matches: Ping(
                id: UUID(uuidString: "a55bf2c4-c270-4d8b-806b-9f993e8d415a")!
            )
        )
    }
}

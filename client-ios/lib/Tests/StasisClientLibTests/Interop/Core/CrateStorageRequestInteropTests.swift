import Foundation
@testable import StasisClientLib
import Testing

@Suite("CrateStorageRequest interop")
struct CrateStorageRequestInteropTests {
    @Test("decode and re-encode a request")
    func request() throws {
        try assert(
            domain: "core",
            resource: "CrateStorageRequest",
            matches: CrateStorageRequest(
                id: UUID(uuidString: "3de796db-0105-4d81-a8da-48f617b4421b")!,
                crate: UUID(uuidString: "daa9dd9e-828d-4810-b34f-736d3b742aad")!,
                size: 8388608,
                copies: 3,
                origin: UUID(uuidString: "c4db10f8-a72c-456f-9f6b-b9ef650e1f3f")!,
                source: UUID(uuidString: "71cb1be7-140d-4a85-b9b1-a3559e11f73f")!
            )
        )
    }
}

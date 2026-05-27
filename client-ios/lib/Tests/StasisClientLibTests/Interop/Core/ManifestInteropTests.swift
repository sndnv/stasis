import Foundation
@testable import StasisClientLib
import Testing

@Suite("Manifest interop")
struct ManifestInteropTests {
    @Test("decode and re-encode a manifest")
    func manifest() throws {
        try assert(
            domain: "core",
            resource: "Manifest",
            matches: Manifest(
                crate: UUID(uuidString: "daa9dd9e-828d-4810-b34f-736d3b742aad")!,
                size: 8388608,
                copies: 3,
                origin: UUID(uuidString: "c4db10f8-a72c-456f-9f6b-b9ef650e1f3f")!,
                source: UUID(uuidString: "71cb1be7-140d-4a85-b9b1-a3559e11f73f")!,
                destinations: [
                    UUID(uuidString: "8c4efe24-1c2d-44de-a437-40f22e1a9aac")!,
                    UUID(uuidString: "341bb614-d228-4cbc-a4cf-ac5afaa1e50b")!
                ],
                created: Date(timeIntervalSince1970: 1770733200)
            )
        )
    }
}

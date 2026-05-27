import Foundation
@testable import StasisClientLib
import Testing

@Suite("Device interop")
struct DeviceInteropTests {
    @Test("decode and re-encode a device")
    func device() throws {
        try assert(
            domain: "devices",
            resource: "Device",
            matches: Device(
                id: UUID(uuidString: "f1e1ed1a-7dc2-4e9c-89fd-bab0ffbaa997")!,
                name: "test-device",
                node: UUID(uuidString: "a9b7d0fb-3751-48fc-8706-9176eca571f1")!,
                owner: UUID(uuidString: "7a1c27c2-0b3c-4f23-a68b-814084bfee7d")!,
                active: true,
                limits: Device.Limits(
                    maxCrates: 100,
                    maxStorage: 1099511627776,
                    maxStoragePerCrate: 10737418240,
                    maxRetention: SecondsDuration(7776000),
                    minRetention: SecondsDuration(86400)
                ),
                created: Date(timeIntervalSince1970: 1772366400),
                updated: Date(timeIntervalSince1970: 1772456400)
            )
        )
    }

    @Test("decode and re-encode a device with null limits")
    func deviceNullLimits() throws {
        try assert(
            domain: "devices",
            resource: "Device.null-limits",
            matches: Device(
                id: UUID(uuidString: "f1e1ed1a-7dc2-4e9c-89fd-bab0ffbaa997")!,
                name: "test-device",
                node: UUID(uuidString: "a9b7d0fb-3751-48fc-8706-9176eca571f1")!,
                owner: UUID(uuidString: "7a1c27c2-0b3c-4f23-a68b-814084bfee7d")!,
                active: true,
                limits: nil,
                created: Date(timeIntervalSince1970: 1772366400),
                updated: Date(timeIntervalSince1970: 1772456400)
            )
        )
    }

    @Test("decode and re-encode a device without limits")
    func deviceNoLimits() throws {
        try assert(
            domain: "devices",
            resource: "Device.no-limits",
            matches: Device(
                id: UUID(uuidString: "f1e1ed1a-7dc2-4e9c-89fd-bab0ffbaa997")!,
                name: "test-device",
                node: UUID(uuidString: "a9b7d0fb-3751-48fc-8706-9176eca571f1")!,
                owner: UUID(uuidString: "7a1c27c2-0b3c-4f23-a68b-814084bfee7d")!,
                active: true,
                limits: nil,
                created: Date(timeIntervalSince1970: 1772366400),
                updated: Date(timeIntervalSince1970: 1772456400)
            )
        )
    }
}

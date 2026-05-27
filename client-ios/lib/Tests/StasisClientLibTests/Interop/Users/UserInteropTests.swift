import Foundation
@testable import StasisClientLib
import Testing

@Suite("User interop")
struct UserInteropTests {
    @Test("decode and re-encode a user")
    func user() throws {
        try assert(
            domain: "users",
            resource: "User",
            matches: User(
                id: UUID(uuidString: "7a1c27c2-0b3c-4f23-a68b-814084bfee7d")!,
                salt: "test-salt-value",
                active: true,
                limits: User.Limits(
                    maxDevices: 10,
                    maxCrates: 100,
                    maxStorage: 1099511627776,
                    maxStoragePerCrate: 10737418240,
                    maxRetention: SecondsDuration(7776000),
                    minRetention: SecondsDuration(86400)
                ),
                permissions: ["view-self"],
                created: Date(timeIntervalSince1970: 1772366400),
                updated: Date(timeIntervalSince1970: 1772456400)
            )
        )
    }

    @Test("decode and re-encode a user with null limits")
    func userNullLimits() throws {
        try assert(
            domain: "users",
            resource: "User.null-limits",
            matches: User(
                id: UUID(uuidString: "7a1c27c2-0b3c-4f23-a68b-814084bfee7d")!,
                salt: "test-salt-value",
                active: true,
                limits: nil,
                permissions: ["view-self"],
                created: Date(timeIntervalSince1970: 1772366400),
                updated: Date(timeIntervalSince1970: 1772456400)
            )
        )
    }

    @Test("decode and re-encode a user without limits")
    func userNoLimits() throws {
        try assert(
            domain: "users",
            resource: "User.no-limits",
            matches: User(
                id: UUID(uuidString: "7a1c27c2-0b3c-4f23-a68b-814084bfee7d")!,
                salt: "test-salt-value",
                active: true,
                limits: nil,
                permissions: ["view-self"],
                created: Date(timeIntervalSince1970: 1772366400),
                updated: Date(timeIntervalSince1970: 1772456400)
            )
        )
    }
}

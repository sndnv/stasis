import Foundation
@testable import StasisClientLib
import Testing

@Suite("ResetUserPassword interop")
struct ResetUserPasswordInteropTests {
    @Test("decode and re-encode a request")
    func request() throws {
        try assert(
            domain: "users",
            resource: "ResetUserPassword",
            matches: ResetUserPassword(rawPassword: "new-password-value")
        )
    }

    @Test("decode and re-encode UpdatedUserSalt")
    func updatedUserSalt() throws {
        try assert(
            domain: "users",
            resource: "UpdatedUserSalt",
            matches: UpdatedUserSalt(salt: "new-salt-value")
        )
    }
}

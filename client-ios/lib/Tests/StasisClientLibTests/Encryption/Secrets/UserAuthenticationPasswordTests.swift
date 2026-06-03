import Foundation
@testable import StasisClientLib
import Testing

@Suite("UserAuthenticationPassword")
struct UserAuthenticationPasswordTests {
    @Test("Hashed: allows extracting the hashed password")
    func hashedExtract() throws {
        let original = "test-password"
        let expected = "dGVzdC1wYXNzd29yZA"
        let pwd = UserAuthenticationPassword.hashed(user: UUID(), hashedPassword: Data(original.utf8))
        #expect(try pwd.extract() == expected)
    }

    @Test("Hashed: fails if extracted more than once")
    func hashedExtractTwiceFails() throws {
        let original = "test-password"
        let expected = "dGVzdC1wYXNzd29yZA"
        let pwd = UserAuthenticationPassword.hashed(user: UUID(), hashedPassword: Data(original.utf8))
        #expect(try pwd.extract() == expected)
        #expect(throws: SecretError.passwordAlreadyExtracted) {
            _ = try pwd.extract()
        }
    }

    @Test("Hashed: produces a digested representation")
    func hashedDigested() {
        let original = "test-password"
        let expectedDigest =
            "c2KtMdtdEMnxjhRjqxjdvpxUgZzO410G0LK09qrg-zqMDL9qSaIgoLRoU2X7ueHOa4_XWAcABv4lXD6B0ddUwA"
        let pwd = UserAuthenticationPassword.hashed(user: UUID(), hashedPassword: Data(original.utf8))
        #expect(pwd.digested() == expectedDigest)
    }

    @Test("Unhashed: allows extracting the raw password")
    func unhashedExtract() throws {
        let original = "test-password"
        let pwd = UserAuthenticationPassword.unhashed(user: UUID(), rawPassword: Data(original.utf8))
        #expect(try pwd.extract() == original)
    }

    @Test("Unhashed: fails if extracted more than once")
    func unhashedExtractTwiceFails() throws {
        let original = "test-password"
        let pwd = UserAuthenticationPassword.unhashed(user: UUID(), rawPassword: Data(original.utf8))
        #expect(try pwd.extract() == original)
        #expect(throws: SecretError.passwordAlreadyExtracted) {
            _ = try pwd.extract()
        }
    }

    @Test("Unhashed: produces a digested representation")
    func unhashedDigested() {
        let original = "test-password"
        let expectedDigest =
            "c2KtMdtdEMnxjhRjqxjdvpxUgZzO410G0LK09qrg-zqMDL9qSaIgoLRoU2X7ueHOa4_XWAcABv4lXD6B0ddUwA"
        let pwd = UserAuthenticationPassword.unhashed(user: UUID(), rawPassword: Data(original.utf8))
        #expect(pwd.digested() == expectedDigest)
    }

    @Test("exposes the user id for each case")
    func exposesUser() {
        let user = UUID()
        let hashed = UserAuthenticationPassword.hashed(user: user, hashedPassword: Data([0x01]))
        let unhashed = UserAuthenticationPassword.unhashed(user: user, rawPassword: Data([0x01]))

        #expect(hashed.user == user)
        #expect(unhashed.user == user)
    }

    @Test("Unhashed: fails to extract when raw password is not valid UTF-8")
    func unhashedExtractInvalidUtf8Fails() {
        let pwd = UserAuthenticationPassword.unhashed(user: UUID(), rawPassword: Data([0xC3, 0x28]))
        #expect(throws: InvalidArgumentError.self) {
            _ = try pwd.extract()
        }
    }

    @Test("equality returns false across different cases")
    func equalityAcrossCases() {
        let user = UUID()
        let bytes = Data([0x01])
        let hashed = UserAuthenticationPassword.hashed(user: user, hashedPassword: bytes)
        let unhashed = UserAuthenticationPassword.unhashed(user: user, rawPassword: bytes)

        #expect(hashed != unhashed)
    }
}

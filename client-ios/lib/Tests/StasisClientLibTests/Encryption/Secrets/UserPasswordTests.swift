import Foundation
@testable import StasisClientLib
import Testing

@Suite("UserPassword")
struct UserPasswordTests {
    @Test("generates a hashed authentication password")
    func generatesHashedAuthenticationPassword() throws {
        let target = SecretsConfigFixtures.testConfig
        let userPassword = UserPassword(
            user: SecretsConfigFixtures.testUser,
            salt: "some-user-salt",
            password: "some-user-password",
            target: SecretConfig(
                derivation: SecretConfig.DerivationConfig(
                    encryption: target.derivation.encryption,
                    authentication: try AuthenticationKeyDerivationConfig(
                        enabled: true,
                        secretSize: target.derivation.authentication.secretSize,
                        iterations: target.derivation.authentication.iterations,
                        saltPrefix: target.derivation.authentication.saltPrefix
                    )
                ),
                encryption: target.encryption
            )
        )

        let expected = Data(base64Encoded:
            "ssDIJULJGAzYLLHS7zPNteKz5jAEDb2Dmz8Ym/TZByR41BZ8nLol4OZlQvtkeAPG+CqB0hx56etnggKMKccH5Q=="
        )!

        #expect(
            userPassword.toAuthenticationPassword()
            == .hashed(user: SecretsConfigFixtures.testUser, hashedPassword: expected)
        )
    }

    @Test("generates an unhashed authentication password")
    func generatesUnhashedAuthenticationPassword() throws {
        let originalPassword = "some-user-password"
        let target = SecretsConfigFixtures.testConfig
        let userPassword = UserPassword(
            user: SecretsConfigFixtures.testUser,
            salt: "some-user-salt",
            password: originalPassword,
            target: SecretConfig(
                derivation: SecretConfig.DerivationConfig(
                    encryption: target.derivation.encryption,
                    authentication: try AuthenticationKeyDerivationConfig(
                        enabled: false,
                        secretSize: target.derivation.authentication.secretSize,
                        iterations: target.derivation.authentication.iterations,
                        saltPrefix: target.derivation.authentication.saltPrefix
                    )
                ),
                encryption: target.encryption
            )
        )

        #expect(
            userPassword.toAuthenticationPassword()
            == .unhashed(user: SecretsConfigFixtures.testUser, rawPassword: Data(originalPassword.utf8))
        )
    }

    @Test("generates a hashed encryption password")
    func generatesHashedEncryptionPassword() {
        let userPassword = UserPassword(
            user: SecretsConfigFixtures.testUser,
            salt: "some-user-salt",
            password: "some-user-password",
            target: SecretsConfigFixtures.testConfig
        )

        let expected = Data(base64Encoded:
            "IrTm/MALVpPlroD3yTH2gPdMEj1sT2G5oQ3zx6NGyBSqWzSc+2o0vkD0LhYtbP5V8PvJ6JiZWsDk8h7rWfS3zA=="
        )!

        #expect(
            userPassword.toHashedEncryptionPassword()
            == UserHashedEncryptionPassword(
                user: SecretsConfigFixtures.testUser,
                hashedPassword: expected,
                target: SecretsConfigFixtures.testConfig
            )
        )
    }

    @Test("does not render its content via description")
    func description() {
        let userPassword = UserPassword(
            user: SecretsConfigFixtures.testUser,
            salt: "some-user-salt",
            password: "some-user-password",
            target: SecretsConfigFixtures.testConfig
        )
        #expect(userPassword.description == "Secret(\(String(reflecting: type(of: userPassword))))")
    }
}

import CommonCrypto
import Foundation

public struct UserPassword: Secret {
    public let user: UserId
    public let salt: String
    private let password: String
    public let target: SecretConfig

    public init(user: UserId, salt: String, password: String, target: SecretConfig) {
        self.user = user
        self.salt = salt
        self.password = password
        self.target = target
    }

    public func toAuthenticationPassword() -> UserAuthenticationPassword {
        if target.derivation.authentication.enabled {
            let hashed = Self.derivePassword(
                password: password,
                salt: "\(target.derivation.authentication.saltPrefix)-authentication-\(salt)",
                iterations: target.derivation.authentication.iterations,
                derivedKeySize: target.derivation.authentication.secretSize
            )
            return .hashed(user: user, hashedPassword: hashed)
        } else {
            return .unhashed(user: user, rawPassword: Data(password.utf8))
        }
    }

    public func toHashedEncryptionPassword() -> UserHashedEncryptionPassword {
        let hashed = Self.derivePassword(
            password: password,
            salt: "\(target.derivation.encryption.saltPrefix)-encryption-\(salt)",
            iterations: target.derivation.encryption.iterations,
            derivedKeySize: target.derivation.encryption.secretSize
        )
        return UserHashedEncryptionPassword(user: user, hashedPassword: hashed, target: target)
    }

    public static func derivePassword(
        password: String,
        salt: String,
        iterations: Int,
        derivedKeySize: Int
    ) -> Data {
        let passwordBytes = Array(password.utf8)
        let saltBytes = Array(salt.utf8)
        var derived = [UInt8](repeating: 0, count: derivedKeySize)

        let status = passwordBytes.withUnsafeBufferPointer { passwordPtr in
            saltBytes.withUnsafeBufferPointer { saltPtr in
                CCKeyDerivationPBKDF(
                    CCPBKDFAlgorithm(kCCPBKDF2),
                    passwordPtr.baseAddress, passwordBytes.count,
                    saltPtr.baseAddress, saltBytes.count,
                    CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA512),
                    UInt32(iterations),
                    &derived, derivedKeySize
                )
            }
        }
        precondition(status == kCCSuccess, "PBKDF2 derivation failed")
        return Data(derived)
    }
}

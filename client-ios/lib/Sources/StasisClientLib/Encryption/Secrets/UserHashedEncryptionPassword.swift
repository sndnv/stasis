import CryptoKit
import Foundation

public struct UserHashedEncryptionPassword: Secret, Equatable {
    public let user: UserId
    private let hashedPassword: Data
    public let target: SecretConfig

    public init(user: UserId, hashedPassword: Data, target: SecretConfig) {
        self.user = user
        self.hashedPassword = hashedPassword
        self.target = target
    }

    public func toLocalEncryptionSecret() -> UserLocalEncryptionSecret {
        let salt = user.bytes
        let keyInfo = Data("\(user.uuidString.lowercased())-encryption-key".utf8)
        let ivInfo = Data("\(user.uuidString.lowercased())-encryption-iv".utf8)

        let (key, iv) = Self.hkdfKeyAndIv(HkdfRequest(
            secret: hashedPassword, salt: salt,
            keyInfo: keyInfo, ivInfo: ivInfo,
            keySize: target.encryption.deviceSecret.keySize,
            ivSize: target.encryption.deviceSecret.ivSize
        ))

        return UserLocalEncryptionSecret(user: user, iv: iv, key: key, target: target)
    }

    public func toKeyStoreEncryptionSecret() -> UserKeyStoreEncryptionSecret {
        let salt = user.bytes
        let keyInfo = Data("\(user.uuidString.lowercased())-key-store-encryption-key".utf8)
        let ivInfo = Data("\(user.uuidString.lowercased())-key-store-encryption-iv".utf8)

        let (key, iv) = Self.hkdfKeyAndIv(HkdfRequest(
            secret: hashedPassword, salt: salt,
            keyInfo: keyInfo, ivInfo: ivInfo,
            keySize: target.encryption.deviceSecret.keySize,
            ivSize: target.encryption.deviceSecret.ivSize
        ))

        return UserKeyStoreEncryptionSecret(user: user, iv: iv, key: key, target: target)
    }

    struct HkdfRequest {
        let secret: Data
        let salt: Data
        let keyInfo: Data
        let ivInfo: Data
        let keySize: Int
        let ivSize: Int
    }

    static func hkdfKeyAndIv(_ request: HkdfRequest) -> (key: Data, iv: Data) {
        let prk = HKDF<SHA512>.extract(
            inputKeyMaterial: SymmetricKey(data: request.secret),
            salt: request.salt
        )
        let key = HKDF<SHA512>.expand(
            pseudoRandomKey: prk,
            info: request.keyInfo,
            outputByteCount: request.keySize
        )
        let iv = HKDF<SHA512>.expand(
            pseudoRandomKey: prk,
            info: request.ivInfo,
            outputByteCount: request.ivSize
        )
        return (
            key.withUnsafeBytes { Data($0) },
            iv.withUnsafeBytes { Data($0) }
        )
    }
}

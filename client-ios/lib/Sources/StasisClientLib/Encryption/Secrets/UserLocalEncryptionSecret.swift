import Foundation

public struct UserLocalEncryptionSecret: Secret, Equatable {
    public let user: UserId
    public let iv: Data
    private let key: Data
    public let target: SecretConfig

    public init(user: UserId, iv: Data, key: Data, target: SecretConfig) {
        self.user = user
        self.iv = iv
        self.key = key
        self.target = target
    }

    public func encryptDeviceSecret(_ secret: DeviceSecret) throws -> Data {
        try secret.encrypted { plaintext in
            try Aes.encrypt(plaintext: plaintext, key: key, iv: iv)
        }
    }

    public func decryptDeviceSecret(device: DeviceId, encryptedSecret: Data) throws -> DeviceSecret {
        try DeviceSecret.decrypted(
            user: user,
            device: device,
            encryptedSecret: encryptedSecret,
            decryptionStage: { ciphertext in
                try Aes.decrypt(ciphertext: ciphertext, key: key, iv: iv)
            },
            target: target
        )
    }
}

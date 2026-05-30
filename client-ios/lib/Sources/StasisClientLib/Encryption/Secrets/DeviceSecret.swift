import CryptoKit
import Foundation

public struct DeviceSecret: Secret, Equatable {
    public static let checksumInfoRadix: Int = 16

    public let user: UserId
    public let device: DeviceId
    public let secret: Data
    public let target: SecretConfig

    public init(user: UserId, device: DeviceId, secret: Data, target: SecretConfig) {
        self.user = user
        self.device = device
        self.secret = secret
        self.target = target
    }

    public func encrypted(_ encryptionStage: (Data) throws -> Data) throws -> Data {
        try encryptionStage(secret)
    }

    public func toFileSecret(forFile: String, checksum: Data) -> DeviceFileSecret {
        let checksumInfo = ChecksumEncoding.string(of: checksum)
        let salt = user.bytes + device.bytes + Data(forFile.utf8)

        let keyInfo = Data(
            "\(user.uuidString.lowercased())-\(device.uuidString.lowercased())-\(forFile)-\(checksumInfo)-key".utf8
        )
        let ivInfo = Data(
            "\(user.uuidString.lowercased())-\(device.uuidString.lowercased())-\(forFile)-\(checksumInfo)-iv".utf8
        )

        let (key, iv) = UserHashedEncryptionPassword.hkdfKeyAndIv(
            UserHashedEncryptionPassword.HkdfRequest(
                secret: secret, salt: salt,
                keyInfo: keyInfo, ivInfo: ivInfo,
                keySize: target.encryption.file.keySize,
                ivSize: target.encryption.file.ivSize
            )
        )

        return DeviceFileSecret(file: forFile, iv: iv, key: key)
    }

    public func toMetadataSecret(metadataCrate: CrateId) -> DeviceMetadataSecret {
        let salt = user.bytes + device.bytes + metadataCrate.bytes

        let keyInfo = Data(
            "\(user.uuidString.lowercased())-\(device.uuidString.lowercased())-\(metadataCrate.uuidString.lowercased())-key".utf8
        )
        let ivInfo = Data(
            "\(user.uuidString.lowercased())-\(device.uuidString.lowercased())-\(metadataCrate.uuidString.lowercased())-iv".utf8
        )

        let (key, iv) = UserHashedEncryptionPassword.hkdfKeyAndIv(
            UserHashedEncryptionPassword.HkdfRequest(
                secret: secret, salt: salt,
                keyInfo: keyInfo, ivInfo: ivInfo,
                keySize: target.encryption.metadata.keySize,
                ivSize: target.encryption.metadata.ivSize
            )
        )

        return DeviceMetadataSecret(iv: iv, key: key)
    }

    public static func decrypted(
        user: UserId,
        device: DeviceId,
        encryptedSecret: Data,
        decryptionStage: (Data) throws -> Data,
        target: SecretConfig
    ) throws -> DeviceSecret {
        let secret = try decryptionStage(encryptedSecret)
        return DeviceSecret(user: user, device: device, secret: secret, target: target)
    }

}

import CryptoKit
import Foundation

public enum Aes {
    // recommended IV size for GCM (96 bits); for more info see https://crypto.stackexchange.com/a/41610
    public static let ivSize: Int = 12 // bytes

    // maximum tag size; for more info see javax.crypto.spec.GCMParameterSpec
    public static let tagSize: Int = 128 // bits

    // various suggestions exist about the max plaintext size for GCM;
    // the limit here is set as 4 GB, well below all suggested maximum sizes
    // for more info see https://crypto.stackexchange.com/q/31793 and https://crypto.stackexchange.com/q/44113
    public static let maximumPlaintextSize: Int64 = 4 * 1024 * 1024 * 1024

    static let tagSizeBytes: Int = tagSize / 8

    public static func encrypt(plaintext: Data, key: Data, iv: Data) throws -> Data {
        let symmetricKey = SymmetricKey(data: key)
        let nonce = try AES.GCM.Nonce(data: iv)
        let sealed = try AES.GCM.seal(plaintext, using: symmetricKey, nonce: nonce)
        return sealed.ciphertext + sealed.tag
    }

    public static func decrypt(ciphertext: Data, key: Data, iv: Data) throws -> Data {
        guard ciphertext.count >= tagSizeBytes else { throw AesError.ciphertextTooShort }
        let symmetricKey = SymmetricKey(data: key)
        let nonce = try AES.GCM.Nonce(data: iv)
        let body = ciphertext.prefix(ciphertext.count - tagSizeBytes)
        let tag = ciphertext.suffix(tagSizeBytes)
        let sealed = try AES.GCM.SealedBox(nonce: nonce, ciphertext: body, tag: tag)
        return try AES.GCM.open(sealed, using: symmetricKey)
    }

    public static func encrypt(_ plaintext: Data, fileSecret: DeviceFileSecret) throws -> Data {
        try fileSecret.encrypt(plaintext)
    }

    public static func encrypt(_ plaintext: Data, metadataSecret: DeviceMetadataSecret) throws -> Data {
        try metadataSecret.encrypt(plaintext)
    }

    public static func decrypt(_ ciphertext: Data, fileSecret: DeviceFileSecret) throws -> Data {
        try fileSecret.decrypt(ciphertext)
    }

    public static func decrypt(_ ciphertext: Data, metadataSecret: DeviceMetadataSecret) throws -> Data {
        try metadataSecret.decrypt(ciphertext)
    }
}

public enum AesError: Error, Equatable {
    case ciphertextTooShort
}

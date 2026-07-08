import CryptoKit
import Foundation

public struct Aes: Encrypting, Decrypting {
    public static let shared = Aes()

    // recommended IV size for GCM (96 bits); for more info see https://crypto.stackexchange.com/a/41610
    public static let ivSize: Int = 12 // bytes

    // maximum tag size; 128 bits is the GCM standard authentication tag length
    public static let tagSize: Int = 128 // bits

    // various suggestions exist about the max plaintext size for GCM;
    // the limit here is set as 4 GB, well below all suggested maximum sizes
    // for more info see https://crypto.stackexchange.com/q/31793 and https://crypto.stackexchange.com/q/44113
    public static let maxPlaintextSize: Int64 = 4 * 1024 * 1024 * 1024

    static let tagSizeBytes: Int = tagSize / 8

    private init() {}

    public var maxPlaintextSize: Int64 { Self.maxPlaintextSize }

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

    public func encrypt(_ plaintext: Data, fileSecret: DeviceFileSecret) throws -> Data {
        try fileSecret.encrypt(plaintext)
    }

    public func encrypt(_ plaintext: Data, metadataSecret: DeviceMetadataSecret) throws -> Data {
        try metadataSecret.encrypt(plaintext)
    }

    public func decrypt(_ ciphertext: Data, fileSecret: DeviceFileSecret) throws -> Data {
        try fileSecret.decrypt(ciphertext)
    }

    public func decrypt(_ ciphertext: Data, metadataSecret: DeviceMetadataSecret) throws -> Data {
        try metadataSecret.decrypt(ciphertext)
    }
}

public enum AesError: Error, Equatable, LocalizedError {
    case ciphertextTooShort

    public var errorDescription: String? {
        switch self {
        case .ciphertextTooShort:
            "Ciphertext is too short to contain an authentication tag"
        }
    }
}

import Foundation

public struct DeviceFileSecret: Secret, Equatable {
    public let file: String
    public let iv: Data
    private let key: Data

    public init(file: String, iv: Data, key: Data) {
        self.file = file
        self.iv = iv
        self.key = key
    }

    public func encrypt(_ plaintext: Data) throws -> Data {
        try Aes.encrypt(plaintext: plaintext, key: key, iv: iv)
    }

    public func decrypt(_ ciphertext: Data) throws -> Data {
        try Aes.decrypt(ciphertext: ciphertext, key: key, iv: iv)
    }
}

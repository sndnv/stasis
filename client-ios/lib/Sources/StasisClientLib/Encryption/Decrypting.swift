import Foundation

public protocol Decrypting: Sendable {
    func decrypt(_ ciphertext: Data, fileSecret: DeviceFileSecret) throws -> Data
    func decrypt(_ ciphertext: Data, metadataSecret: DeviceMetadataSecret) throws -> Data
}

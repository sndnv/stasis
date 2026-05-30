import Foundation

public protocol Encrypting: Sendable {
    var maxPlaintextSize: Int64 { get }
    func encrypt(_ plaintext: Data, fileSecret: DeviceFileSecret) throws -> Data
    func encrypt(_ plaintext: Data, metadataSecret: DeviceMetadataSecret) throws -> Data
}

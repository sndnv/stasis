import Foundation
@testable import StasisClientLib
import Synchronization

final class MockDecrypting: Decrypting, Sendable {
    private let recorded = Mutex<Calls>(Calls())

    struct Calls: Sendable {
        var fileSecret: [(ciphertext: Data, secret: DeviceFileSecret)] = []
        var metadataSecret: [(ciphertext: Data, secret: DeviceMetadataSecret)] = []
    }

    var calls: Calls {
        recorded.withLock { $0 }
    }

    func decrypt(_ ciphertext: Data, fileSecret: DeviceFileSecret) throws -> Data {
        recorded.withLock { $0.fileSecret.append((ciphertext, fileSecret)) }
        return try stripSentinel(ciphertext)
    }

    func decrypt(_ ciphertext: Data, metadataSecret: DeviceMetadataSecret) throws -> Data {
        recorded.withLock { $0.metadataSecret.append((ciphertext, metadataSecret)) }
        return try stripSentinel(ciphertext)
    }

    private func stripSentinel(_ ciphertext: Data) throws -> Data {
        guard ciphertext.first == MockEncrypting.sentinel else {
            throw MockDecryptingError.missingSentinel
        }
        return ciphertext.dropFirst()
    }
}

enum MockDecryptingError: Error, Equatable {
    case missingSentinel
}

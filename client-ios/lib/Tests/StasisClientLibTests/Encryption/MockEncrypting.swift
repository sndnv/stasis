import Foundation
@testable import StasisClientLib
import Synchronization

final class MockEncrypting: Encrypting, Sendable {
    static let sentinel: UInt8 = 0xAE

    let maxPlaintextSize: Int64
    private let recorded = Mutex<Calls>(Calls())

    init(maxPlaintextSize: Int64 = 4 * 1024 * 1024 * 1024) {
        self.maxPlaintextSize = maxPlaintextSize
    }

    struct Calls: Sendable {
        var fileSecret: [(plaintext: Data, secret: DeviceFileSecret)] = []
        var metadataSecret: [(plaintext: Data, secret: DeviceMetadataSecret)] = []
    }

    var calls: Calls {
        recorded.withLock { $0 }
    }

    func encrypt(_ plaintext: Data, fileSecret: DeviceFileSecret) throws -> Data {
        recorded.withLock { $0.fileSecret.append((plaintext, fileSecret)) }
        return Data([Self.sentinel]) + plaintext
    }

    func encrypt(_ plaintext: Data, metadataSecret: DeviceMetadataSecret) throws -> Data {
        recorded.withLock { $0.metadataSecret.append((plaintext, metadataSecret)) }
        return Data([Self.sentinel]) + plaintext
    }
}

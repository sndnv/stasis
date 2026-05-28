import Foundation
import StasisSharedProto

extension UUID {
    var proto: Stasis_ClientIos_Lib_Model_Proto_Uuid {
        var proto = Stasis_ClientIos_Lib_Model_Proto_Uuid()
        withUnsafeBytes(of: uuid) { bytes in
            proto.mostSignificantBits = Int64(
                bigEndian: bytes.loadUnaligned(fromByteOffset: 0, as: Int64.self)
            )
            proto.leastSignificantBits = Int64(
                bigEndian: bytes.loadUnaligned(fromByteOffset: 8, as: Int64.self)
            )
        }
        return proto
    }
}

extension Stasis_ClientIos_Lib_Model_Proto_Uuid {
    var uuid: UUID {
        var most = mostSignificantBits.bigEndian
        var least = leastSignificantBits.bigEndian
        return withUnsafeBytes(of: &most) { msb in
            withUnsafeBytes(of: &least) { lsb in
                UUID(uuid: (
                    msb[0], msb[1], msb[2], msb[3], msb[4], msb[5], msb[6], msb[7],
                    lsb[0], lsb[1], lsb[2], lsb[3], lsb[4], lsb[5], lsb[6], lsb[7]
                ))
            }
        }
    }
}

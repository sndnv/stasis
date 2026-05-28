import Foundation
@testable import StasisClientLib
import StasisSharedProto
import Testing

@Suite("ProtoUuid")
struct ProtoUuidTests {
    @Test("round-trips an all-zero UUID")
    func roundTripsAllZeros() {
        let uuid = UUID(uuid: (0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0))
        let proto = uuid.proto
        #expect(proto.mostSignificantBits == 0)
        #expect(proto.leastSignificantBits == 0)
        #expect(proto.uuid == uuid)
    }

    @Test("round-trips an all-ones UUID and represents both halves as -1")
    func roundTripsAllOnes() {
        let uuid = UUID(uuid: (
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff,
            0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff
        ))
        let proto = uuid.proto
        #expect(proto.mostSignificantBits == -1)
        #expect(proto.leastSignificantBits == -1)
        #expect(proto.uuid == uuid)
    }

    @Test("encodes bytes in big-endian order (most-significant byte first)")
    func encodesBigEndian() {
        let uuid = UUID(uuidString: "01234567-89ab-cdef-0123-456789abcdef")!
        let proto = uuid.proto
        #expect(proto.mostSignificantBits == 0x0123_4567_89ab_cdef)
        #expect(proto.leastSignificantBits == 0x0123_4567_89ab_cdef)
    }

    @Test("decodes Int64 halves into the expected UUID bytes")
    func decodesIntoExpectedBytes() {
        var proto = Stasis_ClientIos_Lib_Model_Proto_Uuid()
        proto.mostSignificantBits = 0x0123_4567_89ab_cdef
        proto.leastSignificantBits = 0x0123_4567_89ab_cdef
        let uuid = proto.uuid
        #expect(uuid.uuidString.lowercased() == "01234567-89ab-cdef-0123-456789abcdef")
    }

    @Test("preserves the high bit (negative Int64 halves)")
    func preservesHighBit() {
        let uuid = UUID(uuid: (
            0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
            0x80, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01
        ))
        let proto = uuid.proto
        #expect(proto.mostSignificantBits == Int64.min)
        #expect(proto.leastSignificantBits == Int64.min + 1)
        #expect(proto.uuid == uuid)
    }

    @Test("round-trips a UUID via its proto form for arbitrary values")
    func roundTripsArbitraryUuids() {
        for _ in 0..<128 {
            let uuid = UUID()
            #expect(uuid.proto.uuid == uuid)
        }
    }

    @Test("round-trips a proto Uuid via its UUID form across the Int64 range")
    func roundTripsArbitraryProtos() {
        let samples: [(Int64, Int64)] = [
            (0, 0),
            (1, -1),
            (Int64.max, Int64.min),
            (Int64.min, Int64.max),
            (0x0123_4567_89ab_cdef, -0x0123_4567_89ab_cdef),
            (-0x7fff_ffff_ffff_ffff, 0x7fff_ffff_ffff_ffff)
        ]
        for (most, least) in samples {
            var proto = Stasis_ClientIos_Lib_Model_Proto_Uuid()
            proto.mostSignificantBits = most
            proto.leastSignificantBits = least
            let recovered = proto.uuid.proto
            #expect(recovered.mostSignificantBits == most)
            #expect(recovered.leastSignificantBits == least)
        }
    }
}

import Foundation
@testable import StasisClientLib
import Testing

@Suite("EndpointAddress")
struct EndpointAddressTests {
    private let encoder = JSONCoders.encoder()
    private let decoder = JSONCoders.decoder()

    @Test("encodes and decodes http")
    func http() throws {
        let address: EndpointAddress = .http(uri: "http://some-address:1234")
        let json = #"{"address_type":"http","address":{"uri":"http://some-address:1234"}}"#

        let encoded = try encoder.encode(address)
        #expect(jsonObject(encoded) == jsonObject(Data(json.utf8)))

        let decoded = try decoder.decode(EndpointAddress.self, from: Data(json.utf8))
        #expect(decoded == address)
    }

    @Test("encodes and decodes grpc")
    func grpc() throws {
        let address: EndpointAddress = .grpc(host: "some-host", port: 1234, tlsEnabled: false)
        let json = #"{"address_type":"grpc","address":{"host":"some-host","port":1234,"tls_enabled":false}}"#

        let encoded = try encoder.encode(address)
        #expect(jsonObject(encoded) == jsonObject(Data(json.utf8)))

        let decoded = try decoder.decode(EndpointAddress.self, from: Data(json.utf8))
        #expect(decoded == address)
    }

    @Test("fails to decode unknown address_type")
    func failsOnUnknown() {
        let json = #"{"address_type":"other","address":{}}"#

        #expect(throws: DecodingError.self) {
            try decoder.decode(EndpointAddress.self, from: Data(json.utf8))
        }
    }

    private func jsonObject(_ data: Data) -> NSDictionary {
        (try? JSONSerialization.jsonObject(with: data)) as? NSDictionary ?? [:]
    }
}

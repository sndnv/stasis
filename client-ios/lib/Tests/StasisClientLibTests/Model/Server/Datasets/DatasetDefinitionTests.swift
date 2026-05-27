import Foundation
@testable import StasisClientLib
import Testing

@Suite("DatasetDefinition.Retention.Policy")
struct DatasetDefinitionRetentionPolicyTests {
    private let encoder = JSONCoders.encoder()
    private let decoder = JSONCoders.decoder()

    @Test("encodes and decodes at-most")
    func atMost() throws {
        let policy: DatasetDefinition.Retention.Policy = .atMost(versions: 3)
        let json = #"{"policy_type":"at-most","versions":3}"#

        let encoded = try encoder.encode(policy)
        #expect(jsonObject(encoded) == jsonObject(Data(json.utf8)))

        let decoded = try decoder.decode(DatasetDefinition.Retention.Policy.self, from: Data(json.utf8))
        #expect(decoded == policy)
    }

    @Test("encodes and decodes latest-only")
    func latestOnly() throws {
        let policy: DatasetDefinition.Retention.Policy = .latestOnly
        let json = #"{"policy_type":"latest-only"}"#

        let encoded = try encoder.encode(policy)
        #expect(jsonObject(encoded) == jsonObject(Data(json.utf8)))

        let decoded = try decoder.decode(DatasetDefinition.Retention.Policy.self, from: Data(json.utf8))
        #expect(decoded == policy)
    }

    @Test("encodes and decodes all")
    func all() throws {
        let policy: DatasetDefinition.Retention.Policy = .all
        let json = #"{"policy_type":"all"}"#

        let encoded = try encoder.encode(policy)
        #expect(jsonObject(encoded) == jsonObject(Data(json.utf8)))

        let decoded = try decoder.decode(DatasetDefinition.Retention.Policy.self, from: Data(json.utf8))
        #expect(decoded == policy)
    }

    @Test("fails to decode unknown policy_type")
    func failsOnUnknown() {
        let json = #"{"policy_type":"other"}"#

        #expect(throws: DecodingError.self) {
            try decoder.decode(DatasetDefinition.Retention.Policy.self, from: Data(json.utf8))
        }
    }

    private func jsonObject(_ data: Data) -> NSDictionary {
        (try? JSONSerialization.jsonObject(with: data)) as? NSDictionary ?? [:]
    }
}

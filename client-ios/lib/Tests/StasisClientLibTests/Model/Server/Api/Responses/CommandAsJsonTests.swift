import Foundation
@testable import StasisClientLib
import Testing

@Suite("CommandAsJson.CommandParametersAsJson")
struct CommandParametersAsJsonTests {
    private let encoder = JSONCoders.encoder()
    private let decoder = JSONCoders.decoder()

    @Test("encodes and decodes an empty command")
    func empty() throws {
        let value = CommandAsJson.CommandParametersAsJson(logoutUser: nil)
        let json = #"{"command_type":"empty"}"#

        let encoded = try encoder.encode(value)
        #expect(jsonObject(encoded) == jsonObject(Data(json.utf8)))

        let decoded = try decoder.decode(CommandAsJson.CommandParametersAsJson.self, from: Data(json.utf8))
        #expect(decoded == value)
    }

    @Test("encodes and decodes a logout_user command with reason")
    func logoutUserWithReason() throws {
        let value = CommandAsJson.CommandParametersAsJson(
            logoutUser: CommandAsJson.LogoutUserCommandAsJson(reason: "test")
        )
        let json = #"{"command_type":"logout_user","logout_user":{"reason":"test"}}"#

        let encoded = try encoder.encode(value)
        #expect(jsonObject(encoded) == jsonObject(Data(json.utf8)))

        let decoded = try decoder.decode(CommandAsJson.CommandParametersAsJson.self, from: Data(json.utf8))
        #expect(decoded == value)
    }

    @Test("encodes and decodes a logout_user command without reason")
    func logoutUserWithoutReason() throws {
        let value = CommandAsJson.CommandParametersAsJson(
            logoutUser: CommandAsJson.LogoutUserCommandAsJson(reason: nil)
        )
        let json = #"{"command_type":"logout_user","logout_user":{}}"#

        let encoded = try encoder.encode(value)
        #expect(jsonObject(encoded) == jsonObject(Data(json.utf8)))

        let decoded = try decoder.decode(CommandAsJson.CommandParametersAsJson.self, from: Data(json.utf8))
        #expect(decoded == value)
    }

    @Test("supports checking if it is empty")
    func isEmpty() {
        let withLogoutUser = CommandAsJson.CommandParametersAsJson(
            logoutUser: CommandAsJson.LogoutUserCommandAsJson(reason: nil)
        )
        let empty = CommandAsJson.CommandParametersAsJson(logoutUser: nil)

        #expect(!withLogoutUser.isEmpty)
        #expect(empty.isEmpty)
    }

    @Test("fails to decode unknown command_type")
    func failsOnUnknown() {
        let json = #"{"command_type":"other"}"#

        #expect(throws: DecodingError.self) {
            try decoder.decode(CommandAsJson.CommandParametersAsJson.self, from: Data(json.utf8))
        }
    }

    private func jsonObject(_ data: Data) -> NSDictionary {
        (try? JSONSerialization.jsonObject(with: data)) as? NSDictionary ?? [:]
    }
}

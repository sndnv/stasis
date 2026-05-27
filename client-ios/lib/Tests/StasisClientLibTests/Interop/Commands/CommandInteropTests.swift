import Foundation
@testable import StasisClientLib
import Testing

@Suite("Command interop")
struct CommandInteropTests {
    @Test("decode and re-encode a logout_user command")
    func logoutUser() throws {
        try assert(
            domain: "commands",
            resource: "Command.logout-user",
            matches: CommandAsJson(
                sequenceId: 42,
                source: "user",
                target: UUID(uuidString: "4e167c18-0808-4f23-886c-598e094a1b6b")!,
                parameters: CommandAsJson.CommandParametersAsJson(
                    logoutUser: CommandAsJson.LogoutUserCommandAsJson(reason: "session timed out")
                ),
                created: Date(timeIntervalSince1970: 1776247200)
            )
        )
    }

    @Test("decode and re-encode a logout_user command without reason")
    func logoutUserNoReason() throws {
        try assert(
            domain: "commands",
            resource: "Command.logout-user-no-reason",
            matches: CommandAsJson(
                sequenceId: 7,
                source: "service",
                target: nil,
                parameters: CommandAsJson.CommandParametersAsJson(
                    logoutUser: CommandAsJson.LogoutUserCommandAsJson(reason: nil)
                ),
                created: Date(timeIntervalSince1970: 1776247200)
            )
        )
    }

    @Test("decode and re-encode an empty command")
    func empty() throws {
        try assert(
            domain: "commands",
            resource: "Command.empty",
            matches: CommandAsJson(
                sequenceId: 0,
                source: "service",
                target: nil,
                parameters: CommandAsJson.CommandParametersAsJson(logoutUser: nil),
                created: Date(timeIntervalSince1970: 1776247200)
            )
        )
    }
}

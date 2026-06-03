import Foundation

public protocol CommandProcessor: Sendable {
    func all() async throws -> [CommandAsJson]
    func latest() async throws -> [CommandAsJson]
    func stop() async
}

public protocol CommandHandlers: Sendable {
    func persistLastProcessedCommand(sequenceId: Int64) async
    func retrieveLastProcessedCommand() async -> Int64
    func executeCommands(commands: [CommandAsJson]) async -> Int64?
}

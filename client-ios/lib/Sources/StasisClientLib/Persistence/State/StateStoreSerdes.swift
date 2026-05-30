import Foundation

public protocol StateStoreSerdes<State>: Sendable {
    associatedtype State: Sendable
    func serialize(_ state: State) throws -> Data
    func deserialize(_ bytes: Data) throws -> State
}

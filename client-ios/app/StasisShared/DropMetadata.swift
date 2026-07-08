import Foundation

struct DropMetadata: Codable, Equatable, Hashable, Sendable {
    let id: String
    let filename: String
    let size: Int64
    let typeIdentifier: String?
    let createdAt: Date

    func encoded() throws -> Data {
        let encoder = JSONEncoder()
        encoder.outputFormatting = .sortedKeys
        return try encoder.encode(self)
    }

    static func decoded(from data: Data) -> DropMetadata? {
        try? JSONDecoder().decode(DropMetadata.self, from: data)
    }
}

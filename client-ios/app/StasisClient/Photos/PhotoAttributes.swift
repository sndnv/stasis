import Foundation

struct PhotoAttributes: Sendable, Equatable, Hashable, Codable {
    let localIdentifier: String
    let favorite: Bool
    let mediaType: PhotoMediaType
    let modificationMillis: Int64?
    let albums: [String]

    func encoded() throws -> Data {
        try JSONEncoder().encode(self)
    }

    static func decoded(from data: Data) -> PhotoAttributes? {
        try? JSONDecoder().decode(PhotoAttributes.self, from: data)
    }
}

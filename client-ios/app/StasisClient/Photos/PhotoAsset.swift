import Foundation

struct PhotoAsset: Sendable, Equatable, Hashable {
    let localIdentifier: String
    let originalFilename: String
    let modificationDate: Date?
    let isFavorite: Bool
    let mediaType: PhotoMediaType
    let byteSize: Int64
    let albums: [String]

    var modificationMillis: Int64? {
        modificationDate.map { Int64(($0.timeIntervalSince1970 * 1000).rounded()) }
    }
}

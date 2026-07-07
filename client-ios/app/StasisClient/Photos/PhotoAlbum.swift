import Foundation

enum PhotoAlbum: Sendable, Equatable, Hashable {
    case all
    case smart(PhotoSmartAlbum)
    case user(String)

    var pathSegment: String {
        switch self {
        case .all: "Library"
        case .smart(let album): album.displayName
        case .user(let title): title
        }
    }
}

import Foundation

enum PhotoSmartAlbum: String, Sendable, Equatable, Hashable, CaseIterable {
    case favorites
    case recents
    case screenshots
    case videos
    case selfies
    case panoramas
    case bursts
    case livePhotos

    var displayName: String {
        switch self {
        case .favorites: "Favorites"
        case .recents: "Recents"
        case .screenshots: "Screenshots"
        case .videos: "Videos"
        case .selfies: "Selfies"
        case .panoramas: "Panoramas"
        case .bursts: "Bursts"
        case .livePhotos: "LivePhotos"
        }
    }

    init?(displayName: String) {
        guard let match = PhotoSmartAlbum.allCases.first(where: { $0.displayName == displayName }) else {
            return nil
        }
        self = match
    }
}

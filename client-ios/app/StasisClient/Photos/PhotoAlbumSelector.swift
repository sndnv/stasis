import Foundation
import StasisClientLib

enum PhotoAlbumSelector {
    static let scheme = "photos"

    static func parse(_ source: String) -> PhotoAlbum? {
        guard SourceUri.scheme(source) == scheme else { return nil }
        let rest = String(source.dropFirst(scheme.count + 1))
        return album(forPath: rest)
    }

    static func album(forPath path: String) -> PhotoAlbum {
        let segment = path.split(separator: "/").first.map(String.init) ?? ""
        return album(forSegment: segment)
    }

    static func album(forSegment segment: String) -> PhotoAlbum {
        if segment.isEmpty || segment == PhotoAlbum.all.pathSegment {
            return .all
        }
        if let smart = PhotoSmartAlbum(displayName: segment) {
            return .smart(smart)
        }
        return .user(segment)
    }
}

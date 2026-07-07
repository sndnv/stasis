import Foundation
@testable import StasisClient
import Testing

@Suite("PhotoAlbumSelector")
struct PhotoAlbumSelectorTests {
    @Test("parses a reserved smart album from a photos source")
    func parsesSmartAlbum() {
        #expect(PhotoAlbumSelector.parse("photos:/Favorites") == .smart(.favorites))
        #expect(PhotoAlbumSelector.parse("photos:/Screenshots") == .smart(.screenshots))
        #expect(PhotoAlbumSelector.parse("photos:/Videos") == .smart(.videos))
    }

    @Test("parses a user album from a photos source")
    func parsesUserAlbum() {
        #expect(PhotoAlbumSelector.parse("photos:/test a") == .user("test a"))
    }

    @Test("parses the whole library when no album segment is present")
    func parsesAllLibrary() {
        #expect(PhotoAlbumSelector.parse("photos:/") == .all)
    }

    @Test("returns nil for a non-photos source")
    func rejectsOtherSchemes() {
        #expect(PhotoAlbumSelector.parse("file:/test") == nil)
        #expect(PhotoAlbumSelector.parse("/test/a") == nil)
    }

    @Test("resolves an album from a stored path segment")
    func resolvesFromPath() {
        #expect(PhotoAlbumSelector.album(forPath: "/Favorites/test.jpg") == .smart(.favorites))
        #expect(PhotoAlbumSelector.album(forPath: "/test a/clip.mov") == .user("test a"))
        #expect(PhotoAlbumSelector.album(forPath: "/Library/test.jpg") == .all)
    }

    @Test("round-trips an album through its path segment")
    func roundTripsSegment() {
        for album in [PhotoAlbum.all, .smart(.favorites), .smart(.bursts), .user("test a")] {
            #expect(PhotoAlbumSelector.album(forSegment: album.pathSegment) == album)
        }
    }
}

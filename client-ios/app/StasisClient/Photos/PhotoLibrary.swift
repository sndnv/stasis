import Foundation

protocol PhotoLibrary: Sendable {
    func hasReadAccess() -> Bool

    func hasWriteAccess() -> Bool

    func assets(in album: PhotoAlbum) async -> [PhotoAsset]

    func candidates(filename: String, in album: PhotoAlbum) async -> [PhotoAsset]

    func materialize(_ asset: PhotoAsset, to destination: URL) async throws

    func createAsset(
        from file: URL,
        filename: String,
        mediaType: PhotoMediaType,
        favorite: Bool,
        album: PhotoAlbum
    ) async throws
}

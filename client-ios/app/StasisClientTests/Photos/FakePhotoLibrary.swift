import Foundation
@testable import StasisClient
import Synchronization

final class FakePhotoLibrary: PhotoLibrary, Sendable {
    struct CreatedAsset: Equatable, Sendable {
        let filename: String
        let mediaType: PhotoMediaType
        let favorite: Bool
        let album: PhotoAlbum
        let bytes: Data
    }

    private struct State: Sendable {
        var readAccess = true
        var writeAccess = true
        var assetsByAlbum: [String: [PhotoAsset]] = [:]
        var bytesByIdentifier: [String: Data] = [:]
        var materializeCount = 0
        var created: [CreatedAsset] = []
    }

    private let state = Mutex(State())

    var materializeCount: Int { state.withLock { $0.materializeCount } }
    var created: [CreatedAsset] { state.withLock { $0.created } }

    func setReadAccess(_ value: Bool) { state.withLock { $0.readAccess = value } }
    func setWriteAccess(_ value: Bool) { state.withLock { $0.writeAccess = value } }

    func addAsset(_ asset: PhotoAsset, bytes: Data, in album: PhotoAlbum) {
        state.withLock {
            $0.assetsByAlbum[album.pathSegment, default: []].append(asset)
            $0.bytesByIdentifier[asset.localIdentifier] = bytes
        }
    }

    func hasReadAccess() -> Bool { state.withLock { $0.readAccess } }

    func hasWriteAccess() -> Bool { state.withLock { $0.writeAccess } }

    func assets(in album: PhotoAlbum) async -> [PhotoAsset] {
        state.withLock { $0.assetsByAlbum[album.pathSegment] ?? [] }
    }

    func candidates(filename: String, in album: PhotoAlbum) async -> [PhotoAsset] {
        await assets(in: album).filter { $0.originalFilename == filename }
    }

    func materialize(_ asset: PhotoAsset, to destination: URL) async throws {
        let bytes = state.withLock { state -> Data? in
            state.materializeCount += 1
            return state.bytesByIdentifier[asset.localIdentifier]
        }
        guard let bytes else {
            throw PhotoLibraryError.assetUnavailable(localIdentifier: asset.localIdentifier)
        }
        try bytes.write(to: destination)
    }

    func createAsset(
        from file: URL,
        filename: String,
        mediaType: PhotoMediaType,
        favorite: Bool,
        album: PhotoAlbum
    ) async throws {
        let bytes = (try? Data(contentsOf: file)) ?? Data()
        state.withLock {
            $0.created.append(
                CreatedAsset(filename: filename, mediaType: mediaType, favorite: favorite, album: album, bytes: bytes)
            )
        }
    }
}

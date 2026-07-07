import Foundation
import Photos

struct PhotoKitPhotoLibrary: PhotoLibrary {
    func hasReadAccess() -> Bool {
        let status = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        return status == .authorized || status == .limited
    }

    func hasWriteAccess() -> Bool {
        hasReadAccess()
    }

    func assets(in album: PhotoAlbum) async -> [PhotoAsset] {
        let fetched: PHFetchResult<PHAsset>
        if let collection = Self.collection(for: album) {
            fetched = PHAsset.fetchAssets(in: collection, options: PHFetchOptions())
        } else if case .all = album {
            fetched = PHAsset.fetchAssets(with: PHFetchOptions())
        } else {
            return []
        }
        return Self.map(fetched)
    }

    func candidates(filename: String, in album: PhotoAlbum) async -> [PhotoAsset] {
        await assets(in: album).filter { $0.originalFilename == filename }
    }

    func materialize(_ asset: PhotoAsset, to destination: URL) async throws {
        guard let phAsset = Self.fetchAsset(localIdentifier: asset.localIdentifier) else {
            throw PhotoLibraryError.assetUnavailable(localIdentifier: asset.localIdentifier)
        }
        guard let resource = Self.primaryResource(for: phAsset) else {
            throw PhotoLibraryError.assetUnavailable(localIdentifier: asset.localIdentifier)
        }
        let options = PHAssetResourceRequestOptions()
        options.isNetworkAccessAllowed = true
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHAssetResourceManager.default().writeData(for: resource, toFile: destination, options: options) { error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }
    }

    func createAsset(
        from file: URL,
        filename: String,
        mediaType: PhotoMediaType,
        favorite: Bool,
        album: PhotoAlbum
    ) async throws {
        if case .user(let title) = album, Self.userAlbum(title: title) == nil {
            try await Self.createUserAlbum(title: title)
        }

        let collection: PHAssetCollection?
        switch album {
        case .user(let title): collection = Self.userAlbum(title: title)
        case .smart, .all: collection = nil
        }
        let isFavorite = favorite || album == .smart(.favorites)

        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHPhotoLibrary.shared().performChanges {
                let request = PHAssetCreationRequest.forAsset()
                let resourceType: PHAssetResourceType = mediaType == .video ? .video : .photo
                request.addResource(with: resourceType, fileURL: file, options: PHAssetResourceCreationOptions())
                request.isFavorite = isFavorite
                if let collection,
                   let placeholder = request.placeholderForCreatedAsset,
                   let change = PHAssetCollectionChangeRequest(for: collection) {
                    change.addAssets([placeholder] as NSArray)
                }
            } completionHandler: { success, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if success {
                    continuation.resume()
                } else {
                    continuation.resume(throwing: PhotoLibraryError.writeFailed)
                }
            }
        }
    }

    private static func createUserAlbum(title: String) async throws {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Void, Error>) in
            PHPhotoLibrary.shared().performChanges {
                _ = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: title)
            } completionHandler: { success, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if success {
                    continuation.resume()
                } else {
                    continuation.resume(throwing: PhotoLibraryError.writeFailed)
                }
            }
        }
    }

    private static func map(_ result: PHFetchResult<PHAsset>) -> [PhotoAsset] {
        var assets: [PhotoAsset] = []
        result.enumerateObjects { asset, _, _ in assets.append(photoAsset(from: asset)) }
        return assets
    }

    private static func photoAsset(from asset: PHAsset) -> PhotoAsset {
        let resource = primaryResource(for: asset)
        let filename = (resource?.originalFilename ?? "\(asset.localIdentifier).dat")
            .replacingOccurrences(of: "/", with: "_")
        return PhotoAsset(
            localIdentifier: asset.localIdentifier,
            originalFilename: filename,
            modificationDate: asset.modificationDate,
            isFavorite: asset.isFavorite,
            mediaType: asset.mediaType == .video ? .video : .image,
            byteSize: 0,
            albums: albumTitles(for: asset)
        )
    }

    private static func albumTitles(for asset: PHAsset) -> [String] {
        let collections = PHAssetCollection.fetchAssetCollectionsContaining(asset, with: .album, options: nil)
        var titles: [String] = []
        collections.enumerateObjects { collection, _, _ in
            if let title = collection.localizedTitle { titles.append(title) }
        }
        return titles
    }

    private static func fetchAsset(localIdentifier: String) -> PHAsset? {
        PHAsset.fetchAssets(withLocalIdentifiers: [localIdentifier], options: nil).firstObject
    }

    private static func primaryResource(for asset: PHAsset) -> PHAssetResource? {
        let resources = PHAssetResource.assetResources(for: asset)
        let preferred: [PHAssetResourceType] = asset.mediaType == .video
            ? [.video, .fullSizeVideo]
            : [.photo, .fullSizePhoto]
        for type in preferred {
            if let match = resources.first(where: { $0.type == type }) { return match }
        }
        return resources.first
    }

    private static func collection(for album: PhotoAlbum) -> PHAssetCollection? {
        switch album {
        case .all: nil
        case .smart(let smart): smartCollection(smart)
        case .user(let title): userAlbum(title: title)
        }
    }

    private static func smartCollection(_ album: PhotoSmartAlbum) -> PHAssetCollection? {
        let subtype: PHAssetCollectionSubtype = switch album {
        case .favorites: .smartAlbumFavorites
        case .recents: .smartAlbumUserLibrary
        case .screenshots: .smartAlbumScreenshots
        case .videos: .smartAlbumVideos
        case .selfies: .smartAlbumSelfPortraits
        case .panoramas: .smartAlbumPanoramas
        case .bursts: .smartAlbumBursts
        case .livePhotos: .smartAlbumLivePhotos
        }
        return PHAssetCollection.fetchAssetCollections(with: .smartAlbum, subtype: subtype, options: nil).firstObject
    }

    private static func userAlbum(title: String) -> PHAssetCollection? {
        let options = PHFetchOptions()
        options.predicate = NSPredicate(format: "localizedTitle = %@", title)
        return PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: options).firstObject
    }
}

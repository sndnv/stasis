import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("PhotosEntityKind")
struct PhotosEntityKindTests {
    private let favoritesRule = Rule(id: 1, operation: .include, source: "photos:/Favorites", pattern: "*", definition: nil)

    @Test("backs up a matched photo, streaming its materialized bytes on read")
    func backsUpAndStreamsContent() async throws {
        let bytes = Data("a streamable photo payload".utf8)
        let asset = imageAsset(filename: "test.jpg", bytes: bytes)
        let library = FakePhotoLibrary()
        library.addAsset(asset, bytes: bytes, in: .smart(.favorites))

        let tracker = RecordingBackupTracker()
        let kind = PhotosEntityKind(library: library)
        let collector = try await kind.collector(
            operation: UUID(),
            collector: .withRules([favoritesRule]),
            latestMetadata: nil,
            providers: backupProviders(tracker: tracker)
        )

        let entities = try await collect(collector.collect())
        #expect(entities.count == 1)
        let entity = try #require(entities.first)
        #expect(entity.ref.key == "photos:/Favorites/test.jpg")
        #expect(entity.currentMetadata.content?.size == Int64(bytes.count))
        #expect(library.materializeCount == 1)
        #expect(tracker.discovered.map(\.key) == ["photos:/Favorites/test.jpg"])

        let read = try await collectData(kind.read(entity: entity, scheme: "photos", path: "/Favorites/test.jpg", chunkSize: 8))
        #expect(read == bytes)

        await #expect(throws: PhotoLibraryError.contentUnavailable(key: entity.ref.key)) {
            _ = try await collectData(
                kind.read(entity: entity, scheme: "photos", path: "/Favorites/test.jpg", chunkSize: 8)
            )
        }
    }

    @Test("reuses existing content without materializing when the modification date is unchanged")
    func reusesUnchangedAsset() async throws {
        let bytes = Data("unchanged photo".utf8)
        let modified = Date(timeIntervalSince1970: 1_700_000_000)
        let asset = imageAsset(filename: "test.jpg", bytes: bytes, modified: modified)
        let library = FakePhotoLibrary()
        library.addAsset(asset, bytes: bytes, in: .smart(.favorites))

        let key = "photos:/Favorites/test.jpg"
        let attributes = try PhotoAttributes(
            localIdentifier: asset.localIdentifier,
            favorite: false,
            mediaType: .image,
            modificationMillis: asset.modificationMillis,
            albums: ["Favorites"]
        ).encoded()
        let existing = EntityMetadata.Library(
            path: key,
            created: modified,
            updated: modified,
            size: Int64(bytes.count),
            checksum: Data("stored-checksum".utf8),
            crates: [:],
            compression: "identity",
            attributes: attributes
        )
        let latest = DatasetMetadata(
            contentChanged: [key: .library(existing)],
            metadataChanged: [:],
            filesystem: FilesystemMetadata(changes: [key])
        )

        let kind = PhotosEntityKind(library: library)
        let collector = try await kind.collector(
            operation: UUID(),
            collector: .withRules([favoritesRule]),
            latestMetadata: latest,
            providers: backupProviders(tracker: RecordingBackupTracker())
        )

        let entities = try await collect(collector.collect())
        let entity = try #require(entities.first)
        #expect(entity.hasContentChanged == false)
        #expect(library.materializeCount == 0)
    }

    @Test("records a failure and collects nothing without read access")
    func skipsWithoutReadAccess() async throws {
        let library = FakePhotoLibrary()
        library.setReadAccess(false)

        let tracker = RecordingBackupTracker()
        let kind = PhotosEntityKind(library: library)
        let collector = try await kind.collector(
            operation: UUID(),
            collector: .withRules([favoritesRule]),
            latestMetadata: nil,
            providers: backupProviders(tracker: tracker)
        )

        let entities = try await collect(collector.collect())
        #expect(entities.isEmpty)
        #expect(tracker.failures == 1)
    }

    @Test("backs up a video, preserving its media type in the attributes")
    func backsUpVideo() async throws {
        let bytes = Data("a short video payload".utf8)
        let asset = videoAsset(filename: "clip.mov", bytes: bytes)
        let library = FakePhotoLibrary()
        library.addAsset(asset, bytes: bytes, in: .smart(.videos))

        let rule = Rule(id: 1, operation: .include, source: "photos:/Videos", pattern: "*", definition: nil)
        let kind = PhotosEntityKind(library: library)
        let collector = try await kind.collector(
            operation: UUID(),
            collector: .withRules([rule]),
            latestMetadata: nil,
            providers: backupProviders(tracker: RecordingBackupTracker())
        )

        let entities = try await collect(collector.collect())
        let entity = try #require(entities.first)
        #expect(entity.ref.key == "photos:/Videos/clip.mov")
        let metadata = try #require(libraryMetadata(entity.currentMetadata))
        #expect(PhotoAttributes.decoded(from: metadata.attributes)?.mediaType == .video)
    }

    @Test("restores a photo that is not already present in the library")
    func restoresMissingPhoto() async throws {
        let bytes = Data("restored photo".utf8)
        let library = FakePhotoLibrary()
        let kind = PhotosEntityKind(library: library)

        try await kind.write(
            entity: try target(path: "/test a/test.jpg", bytes: bytes, mediaType: .image, favorite: true),
            scheme: "photos",
            path: "/test a/test.jpg",
            content: makeStream(bytes),
            providers: recoveryProviders()
        )

        #expect(library.created.count == 1)
        let created = try #require(library.created.first)
        #expect(created.bytes == bytes)
        #expect(created.mediaType == .image)
        #expect(created.favorite == true)
        #expect(created.album == .user("test a"))
    }

    @Test("skips restoring a photo already present with matching size and checksum")
    func skipsAlreadyPresentPhoto() async throws {
        let bytes = Data("already present".utf8)
        let library = FakePhotoLibrary()
        let present = imageAsset(filename: "test.jpg", bytes: bytes)
        library.addAsset(present, bytes: bytes, in: .user("test a"))
        let kind = PhotosEntityKind(library: library)

        try await kind.write(
            entity: try target(path: "/test a/test.jpg", bytes: bytes, mediaType: .image, favorite: false),
            scheme: "photos",
            path: "/test a/test.jpg",
            content: makeStream(bytes),
            providers: recoveryProviders()
        )

        #expect(library.created.isEmpty)
    }

    @Test("restores a photo when a present asset has the same size but different content")
    func restoresChangedPhoto() async throws {
        let restored = Data("restored aaa".utf8)
        let present = Data("present bbbb".utf8)
        #expect(restored.count == present.count)

        let library = FakePhotoLibrary()
        let presentAsset = imageAsset(filename: "test.jpg", bytes: present)
        library.addAsset(presentAsset, bytes: present, in: .user("test a"))
        let kind = PhotosEntityKind(library: library)

        try await kind.write(
            entity: try target(path: "/test a/test.jpg", bytes: restored, mediaType: .image, favorite: false),
            scheme: "photos",
            path: "/test a/test.jpg",
            content: makeStream(restored),
            providers: recoveryProviders()
        )

        #expect(library.created.count == 1)
        #expect(library.created.first?.bytes == restored)
    }

    @Test("throws when restoring without write access")
    func failsRestoreWithoutWriteAccess() async throws {
        let bytes = Data("restored photo".utf8)
        let library = FakePhotoLibrary()
        library.setWriteAccess(false)
        let kind = PhotosEntityKind(library: library)

        await #expect(throws: PhotoLibraryPermissionMissing(scheme: "photos")) {
            try await kind.write(
                entity: try target(path: "/test a/test.jpg", bytes: bytes, mediaType: .image, favorite: false),
                scheme: "photos",
                path: "/test a/test.jpg",
                content: makeStream(bytes),
                providers: recoveryProviders()
            )
        }
    }

    private func imageAsset(filename: String, bytes: Data, modified: Date? = nil) -> PhotoAsset {
        PhotoAsset(
            localIdentifier: UUID().uuidString,
            originalFilename: filename,
            modificationDate: modified,
            isFavorite: false,
            mediaType: .image,
            byteSize: Int64(bytes.count),
            albums: []
        )
    }

    private func videoAsset(filename: String, bytes: Data) -> PhotoAsset {
        PhotoAsset(
            localIdentifier: UUID().uuidString,
            originalFilename: filename,
            modificationDate: nil,
            isFavorite: false,
            mediaType: .video,
            byteSize: Int64(bytes.count),
            albums: []
        )
    }

    private func target(path: String, bytes: Data, mediaType: PhotoMediaType, favorite: Bool) async throws -> TargetEntity {
        let key = "photos:\(path)"
        let attributes = try PhotoAttributes(
            localIdentifier: "stored",
            favorite: favorite,
            mediaType: mediaType,
            modificationMillis: nil,
            albums: []
        ).encoded()
        let existing = EntityMetadata.Library(
            path: key,
            created: Date(timeIntervalSince1970: 1),
            updated: Date(timeIntervalSince1970: 1),
            size: Int64(bytes.count),
            checksum: try await sha256(bytes),
            crates: [:],
            compression: "identity",
            attributes: attributes
        )
        return try TargetEntity(
            ref: EntityRef.default(key: key),
            destination: .default,
            existingMetadata: .library(existing),
            currentMetadata: nil
        )
    }

    private func backupProviders(tracker: RecordingBackupTracker) -> BackupProviders {
        BackupProviders(
            checksum: Checksums.sha256,
            staging: DefaultFileStaging(storeDirectory: nil, prefix: "", suffix: ""),
            compression: Compressions.create(defaultCompression: Identity.shared, disabledExtensions: []),
            encryptor: Aes.shared,
            decryptor: Aes.shared,
            clients: StaticClients(api: MockServerApiEndpointClient(), core: MockServerCoreEndpointClient()),
            track: tracker,
            analytics: NoOpAnalyticsCollector(),
            kinds: []
        )
    }

    private func recoveryProviders() -> RecoveryProviders {
        RecoveryProviders(
            checksum: Checksums.sha256,
            staging: DefaultFileStaging(storeDirectory: nil, prefix: "", suffix: ""),
            compression: Compressions.create(defaultCompression: Identity.shared, disabledExtensions: []),
            decryptor: Aes.shared,
            clients: StaticClients(api: MockServerApiEndpointClient(), core: MockServerCoreEndpointClient()),
            track: RecordingRecoveryTracker(),
            analytics: NoOpAnalyticsCollector(),
            kinds: []
        )
    }

    private func sha256(_ data: Data) async throws -> Data {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try data.write(to: url)
        defer { try? FileManager.default.removeItem(at: url) }
        return try await Checksums.sha256(file: url)
    }

    private func makeStream(_ data: Data) -> AsyncThrowingStream<Data, Error> {
        AsyncThrowingStream { continuation in
            continuation.yield(data)
            continuation.finish()
        }
    }

    private func collect<T>(_ stream: AsyncThrowingStream<T, Error>) async throws -> [T] {
        var items: [T] = []
        for try await item in stream { items.append(item) }
        return items
    }

    private func collectData(_ stream: AsyncThrowingStream<Data, Error>) async throws -> Data {
        var data = Data()
        for try await chunk in stream { data.append(chunk) }
        return data
    }

    private func libraryMetadata(_ metadata: EntityMetadata) -> EntityMetadata.Library? {
        if case .library(let library) = metadata { return library }
        return nil
    }
}

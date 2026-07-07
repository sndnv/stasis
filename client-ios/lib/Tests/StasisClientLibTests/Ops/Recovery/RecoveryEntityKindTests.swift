import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Synchronization
import Testing

@Suite("RecoveryEntityKind")
struct RecoveryEntityKindTests {
    private final class MockLibraryKind: RecoveryLibraryKind {
        let scheme = "photos"
        private let preparedFlag = Mutex(false)

        var prepared: Bool { preparedFlag.withLock { $0 } }

        func collector(
            targetMetadata: DatasetMetadata,
            keep: @escaping @Sendable (String, FilesystemMetadata.EntityState) -> Bool,
            destination: TargetEntity.Destination,
            providers: RecoveryProviders
        ) -> any RecoveryCollector {
            MockRecoveryCollector(files: [])
        }

        func prepare(entity: TargetEntity, scheme: String, path: String, providers: RecoveryProviders) throws {
            preparedFlag.withLock { $0 = true }
        }

        func write(
            entity: TargetEntity,
            scheme: String,
            path: String,
            content: AsyncThrowingStream<Data, Error>,
            providers: RecoveryProviders
        ) async throws {}

        func applyMetadata(entity: TargetEntity, scheme: String, path: String, providers: RecoveryProviders) async throws {}
    }

    @Test("filesystem kind prepares and writes entity content to its destination")
    func filesystemPreparesAndWrites() async throws {
        let targetDirectory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        let targetPath = targetDirectory.appendingPathComponent("nested").appendingPathComponent("file.txt")

        let entity = try TargetEntity(
            ref: .filesystem(targetPath),
            destination: .default,
            existingMetadata: Fixtures.Metadata.fileOne.with(path: targetPath.path),
            currentMetadata: nil
        )
        let providers = makeProviders(kinds: [RecoveryEntityKinds.filesystem])

        try RecoveryEntityKinds.filesystem.prepare(entity: entity, ref: targetPath, providers: providers)
        #expect(FileManager.default.fileExists(atPath: targetPath.deletingLastPathComponent().path))

        try await RecoveryEntityKinds.filesystem.write(
            entity: entity,
            ref: targetPath,
            content: contentStream("hello"),
            providers: providers
        )

        #expect(try String(contentsOf: targetPath, encoding: .utf8) == "hello")
    }

    @Test("dispatcher routes prepare to a matching library kind")
    func dispatcherRoutesPrepareToLibrary() throws {
        let kind = MockLibraryKind()

        try RecoveryEntityKinds.prepare(
            kinds: [kind],
            entity: libraryEntity(scheme: "photos"),
            providers: makeProviders(kinds: [kind])
        )

        #expect(kind.prepared)
    }

    @Test("dispatcher skips prepare when no kind is registered for a library scheme")
    func dispatcherSkipsPrepareForUnknownScheme() throws {
        try RecoveryEntityKinds.prepare(
            kinds: [RecoveryEntityKinds.filesystem],
            entity: libraryEntity(scheme: "unknown"),
            providers: makeProviders(kinds: [RecoveryEntityKinds.filesystem])
        )
    }

    @Test("dispatcher fails to write when no kind is registered for a filesystem ref")
    func dispatcherFailsWriteForUnregisteredFilesystem() async throws {
        await #expect(throws: InvalidArgumentError("No filesystem recovery kind was registered")) {
            try await RecoveryEntityKinds.write(
                kinds: [],
                entity: filesystemEntity(),
                content: contentStream("x"),
                providers: makeProviders(kinds: [])
            )
        }
    }

    @Test("dispatcher routes writes to a matching library kind")
    func dispatcherRoutesWriteToLibrary() async throws {
        try await RecoveryEntityKinds.write(
            kinds: [MockLibraryKind()],
            entity: libraryEntity(scheme: "photos"),
            content: contentStream("x"),
            providers: makeProviders(kinds: [RecoveryEntityKinds.filesystem])
        )
    }

    @Test("dispatcher fails to write when no kind is registered for a library scheme")
    func dispatcherFailsWriteForUnknownScheme() async throws {
        await #expect(throws: InvalidArgumentError("No recovery kind was registered for scheme [unknown]")) {
            try await RecoveryEntityKinds.write(
                kinds: [RecoveryEntityKinds.filesystem],
                entity: libraryEntity(scheme: "unknown"),
                content: contentStream("x"),
                providers: makeProviders(kinds: [RecoveryEntityKinds.filesystem])
            )
        }
    }

    @Test("dispatcher fails to apply metadata when no kind is registered for a filesystem ref")
    func dispatcherFailsApplyMetadataForUnregisteredFilesystem() async throws {
        await #expect(throws: InvalidArgumentError("No filesystem recovery kind was registered")) {
            try await RecoveryEntityKinds.applyMetadata(
                kinds: [],
                entity: filesystemEntity(),
                providers: makeProviders(kinds: [])
            )
        }
    }

    @Test("dispatcher routes apply metadata to a matching library kind")
    func dispatcherRoutesApplyMetadataToLibrary() async throws {
        try await RecoveryEntityKinds.applyMetadata(
            kinds: [MockLibraryKind()],
            entity: libraryEntity(scheme: "photos"),
            providers: makeProviders(kinds: [RecoveryEntityKinds.filesystem])
        )
    }

    @Test("dispatcher fails to apply metadata when no kind is registered for a library scheme")
    func dispatcherFailsApplyMetadataForUnknownScheme() async throws {
        await #expect(throws: InvalidArgumentError("No recovery kind was registered for scheme [unknown]")) {
            try await RecoveryEntityKinds.applyMetadata(
                kinds: [RecoveryEntityKinds.filesystem],
                entity: libraryEntity(scheme: "unknown"),
                providers: makeProviders(kinds: [RecoveryEntityKinds.filesystem])
            )
        }
    }

    private func filesystemEntity() throws -> TargetEntity {
        try TargetEntity(
            ref: .filesystem(URL(fileURLWithPath: Fixtures.Metadata.fileOne.path)),
            destination: .default,
            existingMetadata: Fixtures.Metadata.fileOne,
            currentMetadata: nil
        )
    }

    private func libraryEntity(scheme: String) throws -> TargetEntity {
        try TargetEntity(
            ref: .library(scheme: scheme, path: "/album/img.heic"),
            destination: .default,
            existingMetadata: Fixtures.Metadata.fileOne,
            currentMetadata: nil
        )
    }

    private func contentStream(_ text: String) -> AsyncThrowingStream<Data, Error> {
        AsyncThrowingStream { continuation in
            continuation.yield(Data(text.utf8))
            continuation.finish()
        }
    }

    private func makeProviders(kinds: [any RecoveryEntityKind]) -> RecoveryProviders {
        RecoveryProviders(
            checksum: Checksums.md5,
            staging: DefaultFileStaging(storeDirectory: nil, prefix: "staged-", suffix: ".tmp"),
            compression: MockCompression(),
            decryptor: MockDecrypting(),
            clients: StaticClients(
                api: MockServerApiEndpointClient(),
                core: MockServerCoreEndpointClient()
            ),
            track: MockRecoveryTracker(),
            analytics: NoOpAnalyticsCollector(),
            kinds: kinds
        )
    }
}

import Foundation
@testable import StasisClientLib
import StasisClientLibTestSupport
import Testing

@Suite("BackupEntityKind")
struct BackupEntityKindTests {
    private struct LibraryKind: BackupLibraryKind {
        let scheme: String = "photos"

        func collector(
            operation: OperationId,
            collector: Backup.EntityDiscovery.Collector,
            latestMetadata: DatasetMetadata?,
            providers: BackupProviders
        ) async throws -> any BackupCollector {
            throw InvalidArgumentError("not implemented")
        }

        func read(entity: SourceEntity, scheme: String, path: String, chunkSize: Int) -> AsyncThrowingStream<Data, Error> {
            makeDataStream(Data("library".utf8))
        }
    }

    @Test("filesystem kind reads entity content")
    func filesystemReadsContent() async throws {
        let file = try writeTemporaryFile(contents: "test")
        let entity = try SourceEntity(
            ref: .filesystem(file),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.fileOne
        )

        let content = try await collectData(BackupEntityKinds.filesystem.read(entity: entity, ref: file, chunkSize: 8))
        #expect(String(data: content, encoding: .utf8) == "test")
    }

    @Test("dispatcher routes reads to the filesystem kind")
    func dispatcherRoutesToFilesystem() async throws {
        let file = try writeTemporaryFile(contents: "test")
        let entity = try SourceEntity(
            ref: .filesystem(file),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.fileOne
        )

        let content = try await collectData(
            BackupEntityKinds.read(kinds: [BackupEntityKinds.filesystem], entity: entity, chunkSize: 8)
        )
        #expect(String(data: content, encoding: .utf8) == "test")
    }

    @Test("dispatcher routes reads to a matching library kind")
    func dispatcherRoutesToLibrary() async throws {
        let entity = try SourceEntity(
            ref: .library(scheme: "photos", path: "/album/img.heic"),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.fileOne
        )

        let content = try await collectData(
            BackupEntityKinds.read(kinds: [BackupEntityKinds.filesystem, LibraryKind()], entity: entity, chunkSize: 8)
        )
        #expect(String(data: content, encoding: .utf8) == "library")
    }

    @Test("dispatcher fails when no kind is registered for a filesystem ref")
    func dispatcherFailsForUnregisteredFilesystem() throws {
        let entity = try SourceEntity(
            ref: .filesystem(URL(fileURLWithPath: "/file")),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.fileOne
        )

        #expect(throws: InvalidArgumentError("No filesystem backup kind was registered")) {
            _ = try BackupEntityKinds.read(kinds: [], entity: entity, chunkSize: 8)
        }
    }

    @Test("dispatcher fails when no kind is registered for a library scheme")
    func dispatcherFailsForUnknownScheme() throws {
        let entity = try SourceEntity(
            ref: .library(scheme: "unknown", path: "/x"),
            existingMetadata: nil,
            currentMetadata: Fixtures.Metadata.fileOne
        )

        #expect(throws: InvalidArgumentError("No backup kind was registered for scheme [unknown]")) {
            _ = try BackupEntityKinds.read(kinds: [BackupEntityKinds.filesystem], entity: entity, chunkSize: 8)
        }
    }

    private func writeTemporaryFile(contents: String) throws -> URL {
        let directory = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        let file = directory.appendingPathComponent("file")
        try Data(contents.utf8).write(to: file)
        return file
    }
}

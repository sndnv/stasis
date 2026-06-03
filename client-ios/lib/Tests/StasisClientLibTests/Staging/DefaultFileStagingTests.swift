import Foundation
@testable import StasisClientLib
import Testing

@Suite("DefaultFileStaging")
struct DefaultFileStagingTests {
    @Test("creates temporary staging files")
    func createsTemporaryStagingFiles() async throws {
        let staging = DefaultFileStaging(
            storeDirectory: nil,
            prefix: "staging-test-",
            suffix: ".tmp"
        )

        let file = try await staging.temporary()
        defer { try? FileManager.default.removeItem(at: file) }

        let attributes = try FileManager.default.attributesOfItem(atPath: file.path)
        let permissions = (attributes[.posixPermissions] as? NSNumber)?.int16Value ?? -1
        let size = (attributes[.size] as? NSNumber)?.int64Value ?? -1

        #expect(file.lastPathComponent.hasPrefix("staging-test-"))
        #expect(file.lastPathComponent.hasSuffix(".tmp"))
        #expect(size == 0)
        #expect(permissions == 0o600)
    }

    @Test("creates temporary staging files in a dedicated directory")
    func createsTemporaryStagingFilesInDedicatedDirectory() async throws {
        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent(UUID().uuidString)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: directory) }

        let staging = DefaultFileStaging(
            storeDirectory: directory,
            prefix: "staging-test-",
            suffix: ".tmp"
        )

        let file = try await staging.temporary()
        let attributes = try FileManager.default.attributesOfItem(atPath: file.path)
        let permissions = (attributes[.posixPermissions] as? NSNumber)?.int16Value ?? -1
        let size = (attributes[.size] as? NSNumber)?.int64Value ?? -1

        #expect(file.deletingLastPathComponent().standardizedFileURL == directory.standardizedFileURL)
        #expect(file.lastPathComponent.hasPrefix("staging-test-"))
        #expect(file.lastPathComponent.hasSuffix(".tmp"))
        #expect(size == 0)
        #expect(permissions == 0o600)
    }

    @Test("discards temporary staging files")
    func discardsTemporaryStagingFiles() async throws {
        let staging = DefaultFileStaging(
            storeDirectory: nil,
            prefix: "staging-test-",
            suffix: ".tmp"
        )

        let file = try await staging.temporary()
        #expect(FileManager.default.fileExists(atPath: file.path))

        try await staging.discard(file: file)
        #expect(!FileManager.default.fileExists(atPath: file.path))
    }

    @Test("fails to create a temporary file when the directory does not exist")
    func failsToCreateTemporaryFile() async {
        let missingDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("staging-test-nonexistent-\(UUID().uuidString)")
        let staging = DefaultFileStaging(
            storeDirectory: missingDirectory,
            prefix: "staging-test-",
            suffix: ".tmp"
        )

        await #expect(throws: FileStagingError.self) {
            _ = try await staging.temporary()
        }
    }

    @Test("discards non-existent files without raising")
    func discardsMissingFile() async throws {
        let staging = DefaultFileStaging(
            storeDirectory: nil,
            prefix: "staging-test-",
            suffix: ".tmp"
        )

        let missing = FileManager.default.temporaryDirectory
            .appendingPathComponent("staging-test-\(UUID().uuidString).tmp")
        #expect(!FileManager.default.fileExists(atPath: missing.path))

        try await staging.discard(file: missing)
    }

    @Test("destages incoming files to a target that does not yet exist")
    func destagesToMissingTarget() async throws {
        let staging = DefaultFileStaging(
            storeDirectory: nil,
            prefix: "staging-test-",
            suffix: ".tmp"
        )

        let sourceContent = "source-content"
        let source = try await staging.temporary()
        try Data(sourceContent.utf8).write(to: source)

        let target = FileManager.default.temporaryDirectory
            .appendingPathComponent("staging-test-\(UUID().uuidString).tmp")
        defer { try? FileManager.default.removeItem(at: target) }
        #expect(!FileManager.default.fileExists(atPath: target.path))

        try await staging.destage(from: source, to: target)

        #expect(!FileManager.default.fileExists(atPath: source.path))
        let resulting = try String(contentsOf: target, encoding: .utf8)
        #expect(resulting == sourceContent)
    }

    @Test("destages incoming files")
    func destagesIncomingFiles() async throws {
        let staging = DefaultFileStaging(
            storeDirectory: nil,
            prefix: "staging-test-",
            suffix: ".tmp"
        )

        let sourceContent = "source-content"
        let targetContent = "target-content"

        let source = try await staging.temporary()
        #expect(FileManager.default.fileExists(atPath: source.path))
        try Data(sourceContent.utf8).write(to: source)

        let target = try await staging.temporary()
        defer { try? FileManager.default.removeItem(at: target) }
        #expect(FileManager.default.fileExists(atPath: target.path))
        try Data(targetContent.utf8).write(to: target)

        try await staging.destage(from: source, to: target)
        #expect(!FileManager.default.fileExists(atPath: source.path))

        let resulting = try String(contentsOf: target, encoding: .utf8)
        #expect(resulting == sourceContent)
    }
}

import Foundation
@testable import StasisClientLib
import Testing

@Suite("Metadata")
struct MetadataTests {
    private var sourceFile: URL { AnalysisResources.url("metadata-source-file") }
    private var sourceDirectory: URL { AnalysisResources.url("") }

    @Test("extracts base metadata from a file")
    func extractBaseFile() async throws {
        let metadata = try await Metadata.extractBaseEntityMetadata(entity: sourceFile)
        #expect(metadata.path.path.hasSuffix("analysis/metadata-source-file"))
        #expect(metadata.isDirectory == false)
        #expect(metadata.link == nil)
        #expect(metadata.isHidden == false)
        #expect(metadata.updated > Date(timeIntervalSince1970: 0))
        #expect(!metadata.owner.isEmpty)
        #expect(!metadata.group.isEmpty)
        #expect(!metadata.permissions.isEmpty)
    }

    @Test("extracts base metadata from a directory")
    func extractBaseDirectory() async throws {
        let metadata = try await Metadata.extractBaseEntityMetadata(entity: sourceDirectory)
        #expect(metadata.path.path.hasSuffix("/analysis"))
        #expect(metadata.isDirectory == true)
        #expect(metadata.link == nil)
        #expect(metadata.isHidden == false)
        #expect(metadata.updated > Date(timeIntervalSince1970: 0))
        #expect(!metadata.owner.isEmpty)
        #expect(!metadata.group.isEmpty)
        #expect(!metadata.permissions.isEmpty)
    }

    @Test("collects file crate IDs (source / existing metadata / matching checksum)")
    func cratesSourceMatching() throws {
        guard case .file(let existing) = Fixtures.Metadata.fileOne else { Issue.record(); return }
        let crates = try Metadata.collectCratesForSourceFile(
            existingMetadata: Fixtures.Metadata.fileOne,
            currentChecksum: existing.checksum
        )
        #expect(crates == existing.crates)
    }

    @Test("collects file crate IDs (source / existing metadata / mismatching checksum)")
    func cratesSourceMismatching() throws {
        guard case .file(let existing) = Fixtures.Metadata.fileOne else { Issue.record(); return }
        let crates = try Metadata.collectCratesForSourceFile(
            existingMetadata: Fixtures.Metadata.fileOne,
            currentChecksum: Data([0x42])
        )
        #expect(crates != existing.crates)
    }

    @Test("collects file crate IDs (source / missing metadata)")
    func cratesSourceMissing() throws {
        guard case .file(let existing) = Fixtures.Metadata.fileOne else { Issue.record(); return }
        let crates = try Metadata.collectCratesForSourceFile(
            existingMetadata: nil,
            currentChecksum: existing.checksum
        )
        #expect(crates != existing.crates)
    }

    @Test("fails to collect file crate IDs with directory metadata (source)")
    func cratesSourceRejectsDirectory() {
        #expect {
            try Metadata.collectCratesForSourceFile(
                existingMetadata: Fixtures.Metadata.directoryOne,
                currentChecksum: Data([0x01])
            )
        } throws: { error in
            error as? MetadataError == .expectedFileGotDirectory(path: Fixtures.Metadata.directoryOne.path)
        }
    }

    @Test("collects file crate IDs (target file)")
    func cratesTarget() throws {
        guard case .file(let existing) = Fixtures.Metadata.fileOne else { Issue.record(); return }
        let crates = try Metadata.collectCratesForTargetFile(existingMetadata: Fixtures.Metadata.fileOne)
        #expect(crates == existing.crates)
    }

    @Test("fails to collect file crate IDs with directory metadata (target)")
    func cratesTargetRejectsDirectory() {
        #expect {
            try Metadata.collectCratesForTargetFile(existingMetadata: Fixtures.Metadata.directoryOne)
        } throws: { error in
            error as? MetadataError == .expectedFileGotDirectory(path: Fixtures.Metadata.directoryOne.path)
        }
    }

    @Test("collects file compression (target file)")
    func compressionTarget() throws {
        guard case .file(let existing) = Fixtures.Metadata.fileTwo else { Issue.record(); return }
        let compression = try Metadata.collectCompressionForTargetFile(existingMetadata: Fixtures.Metadata.fileTwo)
        #expect(compression == existing.compression)
    }

    @Test("fails to collect file compression with directory metadata (target)")
    func compressionTargetRejectsDirectory() {
        #expect {
            try Metadata.collectCompressionForTargetFile(existingMetadata: Fixtures.Metadata.directoryOne)
        } throws: { error in
            error as? MetadataError == .expectedFileGotDirectory(path: Fixtures.Metadata.directoryOne.path)
        }
    }

    @Test("extracts metadata from a file")
    func collectEntityMetadataFile() async throws {
        let expectedCrateId = UUID(uuidString: "329efbeb-80a3-42b8-b1dc-79bc0fea7bca")!
        let expectedCratePart = sourceFile.path + "_0"
        let expectedChecksum = Data(base64Encoded: "AP6oDy2wA9TrxFNgI4FKqIU=")!

        let base = try await Metadata.extractBaseEntityMetadata(entity: sourceFile)
        let entity = try await Metadata.collectEntityMetadata(
            currentMetadata: base,
            checksum: Checksums.md5,
            collectCrates: { _ in [expectedCratePart: expectedCrateId] },
            collectCompression: { "test" }
        )

        guard case .file(let file) = entity else { Issue.record("expected file metadata"); return }
        #expect(file.path.hasSuffix("analysis/metadata-source-file"))
        #expect(file.link == nil)
        #expect(file.isHidden == false)
        #expect(file.updated > Date(timeIntervalSince1970: 0))
        #expect(!file.owner.isEmpty)
        #expect(!file.group.isEmpty)
        #expect(!file.permissions.isEmpty)
        #expect(file.size == 26)
        #expect(file.checksum == expectedChecksum)
        #expect(file.crates == [expectedCratePart: expectedCrateId])
        #expect(file.compression == "test")
    }

    @Test("extracts metadata from a directory")
    func collectEntityMetadataDirectory() async throws {
        let base = try await Metadata.extractBaseEntityMetadata(entity: sourceDirectory)
        let entity = try await Metadata.collectEntityMetadata(
            currentMetadata: base,
            checksum: Checksums.md5,
            collectCrates: { _ in [:] },
            collectCompression: { "test" }
        )
        guard case .directory(let directory) = entity else { Issue.record("expected directory metadata"); return }
        #expect(directory.path.hasSuffix("/analysis"))
        #expect(directory.link == nil)
        #expect(directory.isHidden == false)
        #expect(directory.updated > Date(timeIntervalSince1970: 0))
        #expect(!directory.owner.isEmpty)
        #expect(!directory.group.isEmpty)
        #expect(!directory.permissions.isEmpty)
    }

    @Test("applies metadata to a file")
    func applyToFile() async throws {
        let targetFile = FileManager.default.temporaryDirectory
            .appendingPathComponent("metadata-target-file-\(UUID().uuidString)")
        try Data().write(to: targetFile)
        defer { try? FileManager.default.removeItem(at: targetFile) }

        let baseline = try await Metadata.extractBaseEntityMetadata(entity: targetFile)

        let metadata = EntityMetadata.file(.init(
            path: targetFile.path,
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 1_577_836_800),
            updated: Date(timeIntervalSince1970: 1_578_009_600),
            owner: baseline.owner,
            group: baseline.group,
            permissions: "rwxrwxrwx",
            size: 1,
            checksum: Data([0x01]),
            crates: [targetFile.path + "_0": UUID()],
            compression: "none"
        ))

        try await Metadata.applyEntityMetadataTo(metadata: metadata, entity: targetFile)

        let after = try await Metadata.extractBaseEntityMetadata(entity: targetFile)
        #expect(baseline.permissions != metadata.permissions)
        #expect(baseline.updated != metadata.updated)
        #expect(after.owner == metadata.owner)
        #expect(after.group == metadata.group)
        #expect(after.permissions == metadata.permissions)
        #expect(after.updated == metadata.updated)
    }

    @Test("applies metadata to a directory")
    func applyToDirectory() async throws {
        let targetDirectory = FileManager.default.temporaryDirectory
            .appendingPathComponent("metadata-target-directory-\(UUID().uuidString)")
        try FileManager.default.createDirectory(at: targetDirectory, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: targetDirectory) }

        let baseline = try await Metadata.extractBaseEntityMetadata(entity: targetDirectory)

        let metadata = EntityMetadata.directory(.init(
            path: targetDirectory.path,
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 1_577_836_800),
            updated: Date(timeIntervalSince1970: 1_578_009_600),
            owner: baseline.owner,
            group: baseline.group,
            permissions: "rwxrwxrwx"
        ))

        try await Metadata.applyEntityMetadataTo(metadata: metadata, entity: targetDirectory)

        let after = try await Metadata.extractBaseEntityMetadata(entity: targetDirectory)
        #expect(baseline.permissions != metadata.permissions)
        #expect(baseline.updated != metadata.updated)
        #expect(after.owner == metadata.owner)
        #expect(after.group == metadata.group)
        #expect(after.permissions == metadata.permissions)
        #expect(after.updated == metadata.updated)
    }

    @Test("collects metadata for source files with same content")
    func collectSourceSameContent() async throws {
        let expectedChecksum = Data(base64Encoded: "AP6oDy2wA9TrxFNgI4FKqIU=")!
        let existing = EntityMetadata.file(.init(
            path: sourceFile.path,
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 4_102_444_800),
            owner: "root",
            group: "root",
            permissions: "rwxrwxrwx",
            size: 1,
            checksum: expectedChecksum,
            crates: [sourceFile.path + "_0": UUID()],
            compression: "none"
        ))

        let source = try await Metadata.collectSource(
            checksum: Checksums.md5,
            compression: MockCompression(),
            entity: sourceFile,
            existingMetadata: existing
        )
        #expect(source.existingMetadata != nil)
        guard case .file(let file) = source.currentMetadata else { Issue.record("expected file"); return }
        #expect(file.path.hasSuffix("analysis/metadata-source-file"))
        #expect(file.size == 26)
        #expect(file.link == nil)
        #expect(file.isHidden == false)
        #expect(!file.owner.isEmpty)
        #expect(!file.group.isEmpty)
        #expect(!file.permissions.isEmpty)
        #expect(file.checksum == expectedChecksum)
        guard case .file(let existingFile) = existing else { Issue.record(); return }
        #expect(file.crates == existingFile.crates)
    }

    @Test("collects metadata for source files with updated content")
    func collectSourceUpdatedContent() async throws {
        let existing = EntityMetadata.file(.init(
            path: sourceFile.path,
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 4_102_444_800),
            owner: "root",
            group: "root",
            permissions: "rwxrwxrwx",
            size: 1,
            checksum: Data([0x01]),
            crates: [sourceFile.path + "_0": UUID()],
            compression: "none"
        ))

        let expectedChecksum = Data(base64Encoded: "AP6oDy2wA9TrxFNgI4FKqIU=")!
        let source = try await Metadata.collectSource(
            checksum: Checksums.md5,
            compression: MockCompression(),
            entity: sourceFile,
            existingMetadata: existing
        )
        guard case .file(let file) = source.currentMetadata else { Issue.record("expected file"); return }
        #expect(file.size == 26)
        #expect(file.checksum == expectedChecksum)
        guard case .file(let existingFile) = existing else { Issue.record(); return }
        #expect(file.crates != existingFile.crates)
    }

    @Test("collects metadata for existing target files")
    func collectTargetExisting() async throws {
        let existingChecksum = Data([0x01])
        let expectedChecksum = Data(base64Encoded: "AP6oDy2wA9TrxFNgI4FKqIU=")!
        let existing = EntityMetadata.file(.init(
            path: sourceFile.path,
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 4_102_444_800),
            owner: "root",
            group: "root",
            permissions: "rwxrwxrwx",
            size: 1,
            checksum: existingChecksum,
            crates: [sourceFile.path + "_0": UUID()],
            compression: "none"
        ))

        let target = try await Metadata.collectTarget(
            checksum: Checksums.md5,
            entity: sourceFile,
            destination: .default,
            existingMetadata: existing
        )
        #expect(target.existingMetadata == existing)
        guard case .file(let current) = target.currentMetadata else { Issue.record("expected file"); return }
        #expect(current.size == 26)
        #expect(current.checksum == expectedChecksum)
        guard case .file(let existingFile) = existing else { Issue.record(); return }
        #expect(current.crates == existingFile.crates)
    }

    @Test("collects metadata for missing target files")
    func collectTargetMissing() async throws {
        let missing = URL(fileURLWithPath: "/tmp/analysis/metadata-missing-file-\(UUID().uuidString)")
        let existing = EntityMetadata.file(.init(
            path: missing.path,
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 4_102_444_800),
            owner: "root",
            group: "root",
            permissions: "rwxrwxrwx",
            size: 1,
            checksum: Data([0x01]),
            crates: [missing.path + "_0": UUID()],
            compression: "none"
        ))

        let target = try await Metadata.collectTarget(
            checksum: Checksums.md5,
            entity: missing,
            destination: .default,
            existingMetadata: existing
        )
        #expect(target.existingMetadata == existing)
        #expect(target.currentMetadata == nil)
    }
}

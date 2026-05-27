import Foundation
@testable import StasisClientLib
import Testing

@Suite("SourceEntity")
struct SourceEntityTests {
    private let fileMeta = Fixtures.Metadata.fileOne
    private let dirMeta = Fixtures.Metadata.directoryOne

    private var filePath: URL { URL(fileURLWithPath: fileMeta.path) }
    private var dirPath: URL { URL(fileURLWithPath: dirMeta.path) }

    @Test("fails if different entity types provided for current and existing metadata")
    func failsOnTypeMismatch() throws {
        #expect(throws: EntityMetadataMismatch.self) {
            _ = try SourceEntity(
                path: filePath,
                existingMetadata: dirMeta,
                currentMetadata: fileMeta
            )
        }
        #expect(throws: EntityMetadataMismatch.self) {
            _ = try SourceEntity(
                path: filePath,
                existingMetadata: fileMeta,
                currentMetadata: dirMeta
            )
        }
    }

    @Test("determines if its metadata has changed")
    func metadataHasChanged() throws {
        let fileWithoutExisting = try SourceEntity(path: filePath, existingMetadata: nil, currentMetadata: fileMeta)
        let fileWithExisting = try SourceEntity(path: filePath, existingMetadata: fileMeta, currentMetadata: fileMeta)
        let fileWithUpdatedGroup = try SourceEntity(
            path: filePath,
            existingMetadata: fileMeta.withFile { file in
                EntityMetadata.File(
                    path: file.path, link: file.link, isHidden: file.isHidden,
                    created: file.created, updated: file.updated, owner: file.owner,
                    group: "none", permissions: file.permissions,
                    size: file.size, checksum: file.checksum, crates: file.crates, compression: file.compression
                )
            },
            currentMetadata: fileMeta
        )
        let dirWithoutExisting = try SourceEntity(path: dirPath, existingMetadata: nil, currentMetadata: dirMeta)
        let dirWithExisting = try SourceEntity(path: dirPath, existingMetadata: dirMeta, currentMetadata: dirMeta)
        let dirWithUpdatedGroup = try SourceEntity(
            path: dirPath,
            existingMetadata: dirMeta.withDirectory { dir in
                EntityMetadata.Directory(
                    path: dir.path, link: dir.link, isHidden: dir.isHidden,
                    created: dir.created, updated: dir.updated, owner: dir.owner,
                    group: "none", permissions: dir.permissions
                )
            },
            currentMetadata: dirMeta
        )

        #expect(fileWithoutExisting.hasChanged)
        #expect(!fileWithExisting.hasChanged)
        #expect(fileWithUpdatedGroup.hasChanged)
        #expect(dirWithoutExisting.hasChanged)
        #expect(!dirWithExisting.hasChanged)
        #expect(dirWithUpdatedGroup.hasChanged)
    }

    @Test("determines if its content has changed")
    func contentHasChanged() throws {
        let fileWithoutExisting = try SourceEntity(path: filePath, existingMetadata: nil, currentMetadata: fileMeta)
        let fileWithExisting = try SourceEntity(path: filePath, existingMetadata: fileMeta, currentMetadata: fileMeta)
        let fileWithUpdatedSize = try SourceEntity(
            path: filePath,
            existingMetadata: fileMeta.withFile { file in
                EntityMetadata.File(
                    path: file.path, link: file.link, isHidden: file.isHidden,
                    created: file.created, updated: file.updated, owner: file.owner,
                    group: file.group, permissions: file.permissions,
                    size: 0, checksum: file.checksum, crates: file.crates, compression: file.compression
                )
            },
            currentMetadata: fileMeta
        )
        let fileWithUpdatedChecksum = try SourceEntity(
            path: filePath,
            existingMetadata: fileMeta.withFile { file in
                EntityMetadata.File(
                    path: file.path, link: file.link, isHidden: file.isHidden,
                    created: file.created, updated: file.updated, owner: file.owner,
                    group: file.group, permissions: file.permissions,
                    size: file.size, checksum: Data([0x00]), crates: file.crates, compression: file.compression
                )
            },
            currentMetadata: fileMeta
        )
        let dirWithoutExisting = try SourceEntity(path: dirPath, existingMetadata: nil, currentMetadata: dirMeta)
        let dirWithExisting = try SourceEntity(path: dirPath, existingMetadata: dirMeta, currentMetadata: dirMeta)
        let dirWithUpdatedGroup = try SourceEntity(
            path: dirPath,
            existingMetadata: dirMeta.withDirectory { dir in
                EntityMetadata.Directory(
                    path: dir.path, link: dir.link, isHidden: dir.isHidden,
                    created: dir.created, updated: dir.updated, owner: dir.owner,
                    group: "none", permissions: dir.permissions
                )
            },
            currentMetadata: dirMeta
        )

        #expect(fileWithoutExisting.hasContentChanged)
        #expect(!fileWithExisting.hasContentChanged)
        #expect(fileWithUpdatedSize.hasContentChanged)
        #expect(fileWithUpdatedChecksum.hasContentChanged)
        #expect(!dirWithoutExisting.hasContentChanged)
        #expect(!dirWithExisting.hasContentChanged)
        #expect(!dirWithUpdatedGroup.hasContentChanged)
    }
}

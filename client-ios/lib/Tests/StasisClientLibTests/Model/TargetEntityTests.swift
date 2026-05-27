import Foundation
@testable import StasisClientLib
import Testing

@Suite("TargetEntity")
struct TargetEntityTests {
    private let fileMeta = Fixtures.Metadata.fileOne
    private let dirMeta = Fixtures.Metadata.directoryOne

    private var filePath: URL { URL(fileURLWithPath: fileMeta.path) }
    private var dirPath: URL { URL(fileURLWithPath: dirMeta.path) }

    @Test("fails if different entity types provided for current and existing metadata")
    func failsOnTypeMismatch() throws {
        #expect(throws: EntityMetadataMismatch.self) {
            _ = try TargetEntity(
                path: filePath,
                destination: .default,
                existingMetadata: dirMeta,
                currentMetadata: fileMeta
            )
        }
        #expect(throws: EntityMetadataMismatch.self) {
            _ = try TargetEntity(
                path: filePath,
                destination: .default,
                existingMetadata: fileMeta,
                currentMetadata: dirMeta
            )
        }
    }

    @Test("determines if its metadata has changed")
    func metadataHasChanged() throws {
        let fileWithoutCurrent = try TargetEntity(path: filePath, destination: .default, existingMetadata: fileMeta, currentMetadata: nil)
        let fileWithCurrent = try TargetEntity(path: filePath, destination: .default, existingMetadata: fileMeta, currentMetadata: fileMeta)
        let fileWithUpdatedGroup = try TargetEntity(
            path: filePath,
            destination: .default,
            existingMetadata: fileMeta,
            currentMetadata: fileMeta.withFile { file in
                EntityMetadata.File(
                    path: file.path, link: file.link, isHidden: file.isHidden,
                    created: file.created, updated: file.updated, owner: file.owner,
                    group: "none", permissions: file.permissions,
                    size: file.size, checksum: file.checksum, crates: file.crates, compression: file.compression
                )
            }
        )
        let dirWithoutCurrent = try TargetEntity(path: dirPath, destination: .default, existingMetadata: dirMeta, currentMetadata: nil)
        let dirWithCurrent = try TargetEntity(path: dirPath, destination: .default, existingMetadata: dirMeta, currentMetadata: dirMeta)
        let dirWithUpdatedGroup = try TargetEntity(
            path: dirPath,
            destination: .default,
            existingMetadata: dirMeta,
            currentMetadata: dirMeta.withDirectory { dir in
                EntityMetadata.Directory(
                    path: dir.path, link: dir.link, isHidden: dir.isHidden,
                    created: dir.created, updated: dir.updated, owner: dir.owner,
                    group: "none", permissions: dir.permissions
                )
            }
        )

        #expect(fileWithoutCurrent.hasChanged)
        #expect(!fileWithCurrent.hasChanged)
        #expect(fileWithUpdatedGroup.hasChanged)
        #expect(dirWithoutCurrent.hasChanged)
        #expect(!dirWithCurrent.hasChanged)
        #expect(dirWithUpdatedGroup.hasChanged)
    }

    @Test("determines if its content has changed")
    func contentHasChanged() throws {
        let fileWithoutCurrent = try TargetEntity(path: filePath, destination: .default, existingMetadata: fileMeta, currentMetadata: nil)
        let fileWithCurrent = try TargetEntity(path: filePath, destination: .default, existingMetadata: fileMeta, currentMetadata: fileMeta)
        let fileWithUpdatedSize = try TargetEntity(
            path: filePath,
            destination: .default,
            existingMetadata: fileMeta,
            currentMetadata: fileMeta.withFile { file in
                EntityMetadata.File(
                    path: file.path, link: file.link, isHidden: file.isHidden,
                    created: file.created, updated: file.updated, owner: file.owner,
                    group: file.group, permissions: file.permissions,
                    size: 0, checksum: file.checksum, crates: file.crates, compression: file.compression
                )
            }
        )
        let fileWithUpdatedChecksum = try TargetEntity(
            path: filePath,
            destination: .default,
            existingMetadata: fileMeta,
            currentMetadata: fileMeta.withFile { file in
                EntityMetadata.File(
                    path: file.path, link: file.link, isHidden: file.isHidden,
                    created: file.created, updated: file.updated, owner: file.owner,
                    group: file.group, permissions: file.permissions,
                    size: file.size, checksum: Data([0x00]), crates: file.crates, compression: file.compression
                )
            }
        )
        let dirWithoutCurrent = try TargetEntity(path: dirPath, destination: .default, existingMetadata: dirMeta, currentMetadata: nil)
        let dirWithCurrent = try TargetEntity(path: dirPath, destination: .default, existingMetadata: dirMeta, currentMetadata: dirMeta)
        let dirWithUpdatedGroup = try TargetEntity(
            path: dirPath,
            destination: .default,
            existingMetadata: dirMeta,
            currentMetadata: dirMeta.withDirectory { dir in
                EntityMetadata.Directory(
                    path: dir.path, link: dir.link, isHidden: dir.isHidden,
                    created: dir.created, updated: dir.updated, owner: dir.owner,
                    group: "none", permissions: dir.permissions
                )
            }
        )

        #expect(fileWithoutCurrent.hasContentChanged)
        #expect(!fileWithCurrent.hasContentChanged)
        #expect(fileWithUpdatedSize.hasContentChanged)
        #expect(fileWithUpdatedChecksum.hasContentChanged)
        #expect(!dirWithoutCurrent.hasContentChanged)
        #expect(!dirWithCurrent.hasContentChanged)
        #expect(!dirWithUpdatedGroup.hasContentChanged)
    }

    @Test("provides its original file path")
    func providesOriginalPath() throws {
        let target = try TargetEntity(
            path: filePath,
            destination: .default,
            existingMetadata: fileMeta,
            currentMetadata: nil
        )
        #expect(target.originalPath.path == fileMeta.path)
    }

    @Test("provides its destination file path")
    func providesDestinationPath() throws {
        let testDestination = URL(fileURLWithPath: "/tmp/destination")

        let withDefault = try TargetEntity(
            path: filePath,
            destination: .default,
            existingMetadata: fileMeta,
            currentMetadata: nil
        )
        #expect(withDefault.destinationPath == withDefault.originalPath)

        let withDirectoryKeep = try TargetEntity(
            path: filePath,
            destination: .directory(path: testDestination, keepDefaultStructure: true),
            existingMetadata: fileMeta,
            currentMetadata: nil
        )
        #expect(withDirectoryKeep.destinationPath.path == "/tmp/destination/tmp/file/one")

        let withDirectoryFlat = try TargetEntity(
            path: filePath,
            destination: .directory(path: testDestination, keepDefaultStructure: false),
            existingMetadata: fileMeta,
            currentMetadata: nil
        )
        #expect(withDirectoryFlat.destinationPath.path == "/tmp/destination/one")
    }
}

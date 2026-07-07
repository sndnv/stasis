import Foundation
@testable import StasisClientLib
import Testing

@Suite("EntityMetadata")
struct EntityMetadataTests {
    @Test("supports comparing metadata for changes, ignoring file compression")
    func comparesIgnoringCompression() {
        let fileOne = Fixtures.Metadata.fileOne
        let fileTwo = Fixtures.Metadata.fileTwo
        let fileThree = Fixtures.Metadata.fileThree
        let directoryOne = Fixtures.Metadata.directoryOne
        let directoryTwo = Fixtures.Metadata.directoryTwo

        #expect(!fileOne.hasChanged(comparedTo: fileOne))
        #expect(fileOne.hasChanged(comparedTo: fileTwo))
        #expect(fileOne.hasChanged(comparedTo: fileThree))

        let fileOneOtherCompression = fileOne.withFile { file in
            EntityMetadata.File(
                path: file.path, link: file.link, isHidden: file.isHidden,
                created: file.created, updated: file.updated, owner: file.owner,
                group: file.group, permissions: file.permissions,
                size: file.size, checksum: file.checksum, crates: file.crates,
                compression: "other"
            )
        }
        #expect(!fileOne.hasChanged(comparedTo: fileOneOtherCompression))

        #expect(fileOne.hasChanged(comparedTo: directoryOne))
        #expect(!directoryOne.hasChanged(comparedTo: directoryOne))
        #expect(directoryOne.hasChanged(comparedTo: directoryTwo))
    }

    @Test("exposes the inner entity's properties for each case")
    func exposesInnerProperties() throws {
        let file = Fixtures.Metadata.fileTwo
        guard case .file(let fileInner) = file else {
            Issue.record("Expected .file fixture")
            return
        }
        let fileFilesystem = try file.asFilesystem()

        #expect(file.path == fileInner.path)
        #expect(fileFilesystem.link == fileInner.link)
        #expect(fileFilesystem.isHidden == fileInner.isHidden)
        #expect(file.created == fileInner.created)
        #expect(file.updated == fileInner.updated)
        #expect(fileFilesystem.owner == fileInner.owner)
        #expect(fileFilesystem.group == fileInner.group)
        #expect(fileFilesystem.permissions == fileInner.permissions)

        let directory = Fixtures.Metadata.directoryTwo
        guard case .directory(let directoryInner) = directory else {
            Issue.record("Expected .directory fixture")
            return
        }
        let directoryFilesystem = try directory.asFilesystem()

        #expect(directory.path == directoryInner.path)
        #expect(directoryFilesystem.link == directoryInner.link)
        #expect(directoryFilesystem.isHidden == directoryInner.isHidden)
        #expect(directory.created == directoryInner.created)
        #expect(directory.updated == directoryInner.updated)
        #expect(directoryFilesystem.owner == directoryInner.owner)
        #expect(directoryFilesystem.group == directoryInner.group)
        #expect(directoryFilesystem.permissions == directoryInner.permissions)
    }

    @Test("exposes library metadata via the content facet and rejects filesystem access")
    func handlesLibraryMetadata() throws {
        let library = Fixtures.Metadata.libraryOne
        guard case .library(let inner) = library else {
            Issue.record("Expected .library fixture")
            return
        }

        #expect(library.path == inner.path)
        #expect(library.created == inner.created)
        #expect(library.updated == inner.updated)
        #expect(library.filesystem == nil)
        #expect(library.content?.size == inner.size)
        #expect(library.content?.checksum == inner.checksum)
        #expect(library.content?.crates == inner.crates)

        do {
            _ = try library.asFilesystem()
            Issue.record("expected InvalidArgumentError")
        } catch let error as InvalidArgumentError {
            #expect(error.message == "Requested filesystem metadata but library metadata for [photos:/test/test-a] found")
        } catch {
            Issue.record("expected InvalidArgumentError but received [\(error)]")
        }
    }

    @Test("compares library metadata for changes, ignoring compression but not attributes")
    func comparesLibraryIgnoringCompression() {
        let library = Fixtures.Metadata.libraryOne
        guard case .library(let inner) = library else {
            Issue.record("Expected .library fixture")
            return
        }

        let compressionChanged = library.withCompression("gzip")
        let attributesChanged = EntityMetadata.library(EntityMetadata.Library(
            path: inner.path, created: inner.created, updated: inner.updated,
            size: inner.size, checksum: inner.checksum, crates: inner.crates,
            compression: inner.compression, attributes: Data([0x09])
        ))

        #expect(!library.hasChanged(comparedTo: library))
        #expect(!library.hasChanged(comparedTo: compressionChanged))
        #expect(library.hasChanged(comparedTo: attributesChanged))
    }
}

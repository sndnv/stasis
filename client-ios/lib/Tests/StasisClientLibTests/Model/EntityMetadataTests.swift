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
    func exposesInnerProperties() {
        let file = Fixtures.Metadata.fileTwo
        guard case .file(let fileInner) = file else {
            Issue.record("Expected .file fixture")
            return
        }

        #expect(file.path == fileInner.path)
        #expect(file.link == fileInner.link)
        #expect(file.isHidden == fileInner.isHidden)
        #expect(file.created == fileInner.created)
        #expect(file.updated == fileInner.updated)
        #expect(file.owner == fileInner.owner)
        #expect(file.group == fileInner.group)
        #expect(file.permissions == fileInner.permissions)

        let directory = Fixtures.Metadata.directoryTwo
        guard case .directory(let directoryInner) = directory else {
            Issue.record("Expected .directory fixture")
            return
        }

        #expect(directory.path == directoryInner.path)
        #expect(directory.link == directoryInner.link)
        #expect(directory.isHidden == directoryInner.isHidden)
        #expect(directory.created == directoryInner.created)
        #expect(directory.updated == directoryInner.updated)
        #expect(directory.owner == directoryInner.owner)
        #expect(directory.group == directoryInner.group)
        #expect(directory.permissions == directoryInner.permissions)
    }
}

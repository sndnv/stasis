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
}

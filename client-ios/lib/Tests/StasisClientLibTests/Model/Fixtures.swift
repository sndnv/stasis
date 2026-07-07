import Foundation
@testable import StasisClientLib
import StasisSharedProto

enum Fixtures {
    enum Metadata {
        static let fileOne = EntityMetadata.file(.init(
            path: "/tmp/file/one",
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 4_102_444_800),
            owner: "root",
            group: "root",
            permissions: "rwxrwxrwx",
            size: 1,
            checksum: Data([0x01]),
            crates: [
                "/tmp/file/one_0": UUID(uuidString: "329efbeb-80a3-42b8-b1dc-79bc0fea7bca")!
            ],
            compression: "none"
        ))

        static let fileTwo = EntityMetadata.file(.init(
            path: "/tmp/file/two",
            link: "/tmp/file/three",
            isHidden: false,
            created: Date(timeIntervalSince1970: 4_102_444_800),
            updated: Date(timeIntervalSince1970: 0),
            owner: "root",
            group: "root",
            permissions: "rwxrwxrwx",
            size: 2,
            checksum: Data([0x2a]),
            crates: [
                "/tmp/file/two_0": UUID(uuidString: "e672a956-1a95-4304-8af0-9418f0e43cba")!
            ],
            compression: "gzip"
        ))

        static let fileThree = EntityMetadata.file(.init(
            path: "/tmp/file/four",
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 4_102_444_800),
            updated: Date(timeIntervalSince1970: 0),
            owner: "root",
            group: "root",
            permissions: "rwxrwxrwx",
            size: 2,
            checksum: Data([0x00]),
            crates: [
                "/tmp/file/four_0": UUID(uuidString: "7c98df29-a544-41e5-95ac-463987894fac")!
            ],
            compression: "deflate"
        ))

        static let directoryTwo = EntityMetadata.directory(.init(
            path: "/tmp/directory/two",
            link: "/tmp/file/three",
            isHidden: false,
            created: Date(timeIntervalSince1970: 4_102_444_800),
            updated: Date(timeIntervalSince1970: 0),
            owner: "root",
            group: "root",
            permissions: "rwxrwxrwx"
        ))

        static let directoryOne = EntityMetadata.directory(.init(
            path: "/tmp/directory/one",
            link: nil,
            isHidden: false,
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 4_102_444_800),
            owner: "root",
            group: "root",
            permissions: "rwxrwxrwx"
        ))

        static let libraryOne = EntityMetadata.library(.init(
            path: "photos:/test/test-a",
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 4_102_444_800),
            size: 4,
            checksum: Data([0x04]),
            crates: [
                "photos:/test/test-a_0": UUID(uuidString: "7e533be5-6a4b-47fb-ad9e-9db437678c60")!
            ],
            compression: "none",
            attributes: Data([0x01, 0x02])
        ))
    }

    enum Proto {
        enum Metadata {
            static let fileOneMetadataProto: Stasis_ClientIos_Lib_Model_Proto_EntityMetadata = {
                var file = Stasis_ClientIos_Lib_Model_Proto_FileMetadata()
                file.path = "/tmp/file/one"
                file.size = 1
                file.link = ""
                file.isHidden = false
                file.created = 0
                file.updated = 4_102_444_800
                file.owner = "root"
                file.group = "root"
                file.permissions = "rwxrwxrwx"
                file.checksum = Data([0x01])
                file.crates = [
                    "/tmp/file/one_0": UUID(uuidString: "329efbeb-80a3-42b8-b1dc-79bc0fea7bca")!.proto
                ]
                file.compression = "none"
                var entity = Stasis_ClientIos_Lib_Model_Proto_EntityMetadata()
                entity.entity = .file(file)
                return entity
            }()

            static let fileTwoMetadataProto: Stasis_ClientIos_Lib_Model_Proto_EntityMetadata = {
                var file = Stasis_ClientIos_Lib_Model_Proto_FileMetadata()
                file.path = "/tmp/file/two"
                file.size = 2
                file.link = "/tmp/file/three"
                file.isHidden = false
                file.created = 4_102_444_800
                file.updated = 0
                file.owner = "root"
                file.group = "root"
                file.permissions = "rwxrwxrwx"
                file.checksum = Data([0x2a])
                file.crates = [
                    "/tmp/file/two_0": UUID(uuidString: "e672a956-1a95-4304-8af0-9418f0e43cba")!.proto
                ]
                file.compression = "gzip"
                var entity = Stasis_ClientIos_Lib_Model_Proto_EntityMetadata()
                entity.entity = .file(file)
                return entity
            }()

            static let directoryOneMetadataProto: Stasis_ClientIos_Lib_Model_Proto_EntityMetadata = {
                var directory = Stasis_ClientIos_Lib_Model_Proto_DirectoryMetadata()
                directory.path = "/tmp/directory/one"
                directory.link = ""
                directory.isHidden = false
                directory.created = 0
                directory.updated = 4_102_444_800
                directory.owner = "root"
                directory.group = "root"
                directory.permissions = "rwxrwxrwx"
                var entity = Stasis_ClientIos_Lib_Model_Proto_EntityMetadata()
                entity.entity = .directory(directory)
                return entity
            }()

            static let directoryTwoMetadataProto: Stasis_ClientIos_Lib_Model_Proto_EntityMetadata = {
                var directory = Stasis_ClientIos_Lib_Model_Proto_DirectoryMetadata()
                directory.path = "/tmp/directory/two"
                directory.link = "/tmp/file/three"
                directory.isHidden = false
                directory.created = 4_102_444_800
                directory.updated = 0
                directory.owner = "root"
                directory.group = "root"
                directory.permissions = "rwxrwxrwx"
                var entity = Stasis_ClientIos_Lib_Model_Proto_EntityMetadata()
                entity.entity = .directory(directory)
                return entity
            }()

            static let libraryOneMetadataProto: Stasis_ClientIos_Lib_Model_Proto_EntityMetadata = {
                var library = Stasis_ClientIos_Lib_Model_Proto_LibraryMetadata()
                library.key = "photos:/test/test-a"
                library.size = 4
                library.created = 0
                library.updated = 4_102_444_800
                library.checksum = Data([0x04])
                library.crates = [
                    "photos:/test/test-a_0": UUID(uuidString: "7e533be5-6a4b-47fb-ad9e-9db437678c60")!.proto
                ]
                library.compression = "none"
                library.attributes = Data([0x01, 0x02])
                var entity = Stasis_ClientIos_Lib_Model_Proto_EntityMetadata()
                entity.entity = .library(library)
                return entity
            }()

            static let emptyMetadataProto = Stasis_ClientIos_Lib_Model_Proto_EntityMetadata()
        }
    }
}

extension EntityMetadata {
    func withFile(_ transform: (EntityMetadata.File) -> EntityMetadata.File) -> EntityMetadata {
        guard case .file(let file) = self else { return self }
        return .file(transform(file))
    }

    func withDirectory(_ transform: (EntityMetadata.Directory) -> EntityMetadata.Directory) -> EntityMetadata {
        guard case .directory(let directory) = self else { return self }
        return .directory(transform(directory))
    }
}

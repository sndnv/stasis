import Foundation
@testable import StasisClientLib

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

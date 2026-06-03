import Foundation

struct FileSystemSetup: Sendable {
    let chars: [Character]
    let disallowedChars: [Character]
    let disallowedFileNames: [String]
    let maxFilesPerDir: Int
    let nestedParentDirs: Int
    let caseSensitive: Bool

    func with(chars: [Character]? = nil, maxFilesPerDir: Int? = nil, nestedParentDirs: Int? = nil) -> FileSystemSetup {
        FileSystemSetup(
            chars: chars ?? self.chars,
            disallowedChars: disallowedChars,
            disallowedFileNames: disallowedFileNames,
            maxFilesPerDir: maxFilesPerDir ?? self.maxFilesPerDir,
            nestedParentDirs: nestedParentDirs ?? self.nestedParentDirs,
            caseSensitive: caseSensitive
        )
    }

    static let defaultChars: [Character] = Array("abcdefghijklmnopqrstuvwxyz0123456789")
    static let alphaNumericChars: [Character] = Array("abcdefghijklmnopqrstuvwxyz0123456789")

    static let unix = FileSystemSetup(
        chars: defaultChars,
        disallowedChars: ["\u{0000}", "/", "\n", "\r"],
        disallowedFileNames: [".", ".."],
        maxFilesPerDir: .max,
        nestedParentDirs: 4,
        caseSensitive: true
    )

    static let empty = unix.with(maxFilesPerDir: 0, nestedParentDirs: 0)
}

struct FileSystemObjects: Sendable {
    let filesPerDir: Int
    let rootDirs: Int
    let nestedParentDirs: Int
    let nestedChildDirsPerParent: Int
    let nestedDirs: Int

    var total: Int { filesPerDir + rootDirs * filesPerDir + nestedDirs * filesPerDir }
}

struct RuleExpectation: Sendable {
    let excluded: Int
    let included: Int
    let root: Int
}

func createMockFileSystem(setup: FileSystemSetup) throws -> (TempFilesystem, FileSystemObjects) {
    let filesystem = try TempFilesystem()

    let chars: Set<Character> = Set(
        setup.chars
            .map { setup.caseSensitive ? $0 : Character($0.lowercased()) }
            .filter { !setup.disallowedChars.contains($0) }
    )

    let rootDirectories = chars.map { "root-dir-\($0)" }
    let nestedParentDirs = (0...setup.nestedParentDirs).map { "root/parent-\($0)" }
    let nestedDirectories = chars.flatMap { char in
        nestedParentDirs.map { "\($0)/child-dir-\(char)" }
    }

    let files = chars
        .map { String($0) }
        .filter { !setup.disallowedFileNames.contains($0) }
        .prefix(setup.maxFilesPerDir)

    for file in files {
        try filesystem.createFile(file)
    }

    for directory in rootDirectories {
        try filesystem.createDirectory(directory)
        for file in files {
            try filesystem.createFile("\(directory)/\(file)")
        }
    }

    for directory in nestedDirectories {
        try filesystem.createDirectory(directory)
        for file in files {
            try filesystem.createFile("\(directory)/\(file)")
        }
    }

    return (
        filesystem,
        FileSystemObjects(
            filesPerDir: files.count,
            rootDirs: rootDirectories.count,
            nestedParentDirs: nestedParentDirs.count,
            nestedChildDirsPerParent: chars.count,
            nestedDirs: nestedDirectories.count
        )
    )
}

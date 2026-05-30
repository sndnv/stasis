import Foundation

public final class StateStore<State: Sendable>: Sendable {
    public static var minRetainedVersions: Int { 2 }

    private let target: URL
    private let retainedVersions: Int
    private let serdes: any StateStoreSerdes<State>

    public convenience init(target: URL, serdes: any StateStoreSerdes<State>) throws {
        try self.init(target: target, retainedVersions: Self.minRetainedVersions, serdes: serdes)
    }

    public init(target: URL, retainedVersions: Int, serdes: any StateStoreSerdes<State>) throws {
        self.target = target
        self.retainedVersions = retainedVersions
        self.serdes = serdes
        try FileManager.default.createDirectory(at: target, withIntermediateDirectories: true)
    }

    public func persist(_ state: State) async throws {
        let serialized = try serdes.serialize(state)
        let timestamp = String(Int64(Date().timeIntervalSince1970 * 1000))

        try FileManager.default.createDirectory(at: target, withIntermediateDirectories: true)
        try serialized.write(to: target.appendingPathComponent("state_\(timestamp)"))

        try await prune(keep: retainedVersions)
    }

    public func discard() async throws {
        try await prune(keep: 0)
    }

    public func prune(keep: Int) async throws {
        let files = try collectStateFiles()
        for file in files.dropLast(keep) {
            try FileManager.default.removeItem(at: file)
        }
    }

    public func restore() async throws -> State? {
        for file in try collectStateFiles().reversed() {
            let bytes = try Data(contentsOf: file)
            if let value = try? serdes.deserialize(bytes) {
                return value
            }
        }
        return nil
    }

    private func collectStateFiles() throws -> [URL] {
        let contents = try FileManager.default.contentsOfDirectory(
            at: target,
            includingPropertiesForKeys: [.isDirectoryKey],
            options: []
        )
        let stateFiles = try contents.filter { url in
            let isDirectory = try url.resourceValues(forKeys: [.isDirectoryKey]).isDirectory ?? false
            return !isDirectory && url.lastPathComponent.hasPrefix("state_")
        }
        return stateFiles.sorted { $0.lastPathComponent < $1.lastPathComponent }
    }
}

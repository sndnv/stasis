public struct EntityMetadataMismatch: Error, Equatable {
    public let currentPath: String
    public let existingPath: String

    public init(currentPath: String, existingPath: String) {
        self.currentPath = currentPath
        self.existingPath = existingPath
    }

    public var message: String {
        "Mismatched current metadata for [\(currentPath)] and existing metadata for [\(existingPath)]"
    }
}

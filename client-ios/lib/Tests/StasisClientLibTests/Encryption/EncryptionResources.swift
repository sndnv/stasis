import Foundation

enum EncryptionResources {
    static func load(_ resource: String) -> Data {
        let url = baseDirectory.appendingPathComponent(resource)
        do {
            return try Data(contentsOf: url)
        } catch {
            fatalError("Encryption resource not found: [\(url.path)]")
        }
    }

    private static let baseDirectory: URL = {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Resources")
            .appendingPathComponent("encryption")
    }()
}

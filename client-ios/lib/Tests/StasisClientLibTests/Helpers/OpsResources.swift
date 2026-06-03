import Foundation

enum OpsResources {
    static func url(_ resource: String) -> URL {
        baseDirectory.appendingPathComponent(resource)
    }

    private static let baseDirectory: URL = {
        URL(fileURLWithPath: #filePath)
            .deletingLastPathComponent()
            .deletingLastPathComponent()
            .appendingPathComponent("Resources")
            .appendingPathComponent("ops")
    }()
}

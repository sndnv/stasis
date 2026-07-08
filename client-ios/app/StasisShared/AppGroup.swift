import Foundation

enum AppGroup {
    static let identifier = "group.stasis.client.ios"

    static func containerURL() -> URL? {
        FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: identifier)
    }
}

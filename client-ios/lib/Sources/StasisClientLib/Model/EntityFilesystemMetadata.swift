import Foundation

public protocol EntityFilesystemMetadata: Sendable {
    var link: String? { get }
    var isHidden: Bool { get }
    var owner: String { get }
    var group: String { get }
    var permissions: String { get }
}

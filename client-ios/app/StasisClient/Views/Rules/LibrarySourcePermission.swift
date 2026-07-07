import Foundation

protocol LibrarySourcePermission: Sendable {
    func status() -> LibraryPermissionStatus
    func request() async -> Bool
}

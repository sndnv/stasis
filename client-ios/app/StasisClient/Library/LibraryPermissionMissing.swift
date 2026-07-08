import Foundation

struct LibraryPermissionMissing: Error, Equatable, LocalizedError {
    let scheme: String

    var errorDescription: String? { "Permission required to access the [\(scheme)] library" }
}

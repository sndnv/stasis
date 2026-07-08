import Foundation

struct PhotoLibraryPermissionMissing: Error, Equatable, LocalizedError {
    let scheme: String

    var errorDescription: String? { "Permission required to access the photo library" }
}

import Foundation

struct PhotoLibraryPermissionMissing: Error, Equatable {
    let scheme: String
}

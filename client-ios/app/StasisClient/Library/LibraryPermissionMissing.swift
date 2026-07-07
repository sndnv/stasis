import Foundation

struct LibraryPermissionMissing: Error, Equatable {
    let scheme: String
}

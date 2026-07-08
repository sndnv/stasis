import Foundation

struct LibraryContentUnavailable: Error, Equatable, LocalizedError {
    let key: String

    var errorDescription: String? { "No backed-up content is available for [\(key)]" }
}

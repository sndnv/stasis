import Foundation

enum PhotoLibraryError: Error, Equatable, LocalizedError {
    case contentUnavailable(key: String)
    case assetUnavailable(localIdentifier: String)
    case writeFailed

    var errorDescription: String? {
        switch self {
        case .contentUnavailable(let key):
            "No photo content is available for [\(key)]"
        case .assetUnavailable(let localIdentifier):
            "The photo [\(localIdentifier)] is no longer available"
        case .writeFailed:
            "Failed to write to the photo library"
        }
    }
}

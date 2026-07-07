import Foundation

enum PhotoLibraryError: Error, Equatable {
    case contentUnavailable(key: String)
    case assetUnavailable(localIdentifier: String)
    case writeFailed
}

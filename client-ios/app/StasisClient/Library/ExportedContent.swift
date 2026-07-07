import Foundation

struct ExportedContent: Equatable, Sendable {
    var fileExtension: String
    var mimeType: String
    var bytes: Data
}

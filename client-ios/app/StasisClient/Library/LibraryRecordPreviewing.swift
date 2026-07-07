import Foundation

protocol LibraryRecordPreviewing: Sendable {
    var scheme: String { get }

    func preview(bytes: Data) throws -> EntityPreview

    func export(bytes: Data) throws -> ExportedContent
}

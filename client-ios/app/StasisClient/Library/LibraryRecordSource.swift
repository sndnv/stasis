import Foundation

protocol LibraryRecordSource: Sendable {
    associatedtype Record: Codable & Sendable

    var scheme: String { get }

    func hasReadAccess() -> Bool

    func hasWriteAccess() -> Bool

    func list() async throws -> [Record]

    func restore(_ record: Record) async throws

    func id(_ record: Record) -> String

    func displayName(_ record: Record) -> String

    func attributes(_ record: Record) -> [String: String]

    func describe(_ record: Record) -> EntityPreview

    func export(_ record: Record) throws -> ExportedContent
}

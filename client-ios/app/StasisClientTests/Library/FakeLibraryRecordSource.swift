import Foundation
@testable import StasisClient
import Synchronization

final class FakeLibraryRecordSource: LibraryRecordSource {
    struct Record: Codable, Sendable, Equatable, Hashable {
        var id: String
        var name: String
        var payload: String
    }

    private struct State {
        var records: [Record]
        var restored: [Record]
        var readAccess: Bool
        var writeAccess: Bool
    }

    let scheme: String
    private let state: Mutex<State>

    init(scheme: String, records: [Record], readAccess: Bool, writeAccess: Bool) {
        self.scheme = scheme
        self.state = Mutex(State(records: records, restored: [], readAccess: readAccess, writeAccess: writeAccess))
    }

    var restored: [Record] {
        state.withLock { $0.restored }
    }

    func hasReadAccess() -> Bool {
        state.withLock { $0.readAccess }
    }

    func hasWriteAccess() -> Bool {
        state.withLock { $0.writeAccess }
    }

    func list() async throws -> [Record] {
        state.withLock { $0.records }
    }

    func restore(_ record: Record) async throws {
        state.withLock { $0.restored.append(record) }
    }

    func id(_ record: Record) -> String {
        record.id
    }

    func displayName(_ record: Record) -> String {
        record.name
    }

    func attributes(_ record: Record) -> [String: String] {
        ["name": record.name]
    }

    func describe(_ record: Record) -> EntityPreview {
        EntityPreview(sections: [
            EntityPreview.Section(title: "Record", fields: [
                EntityPreview.Field(label: "Name", value: record.name),
                EntityPreview.Field(label: "Payload", value: record.payload)
            ])
        ])
    }

    func export(_ record: Record) throws -> ExportedContent {
        ExportedContent(fileExtension: "txt", mimeType: "text/plain", bytes: Data(record.payload.utf8))
    }
}

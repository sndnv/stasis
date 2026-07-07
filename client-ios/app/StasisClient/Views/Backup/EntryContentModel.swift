import Foundation
import Observation
import StasisClientLib
import UIKit

@MainActor
@Observable
final class EntryContentModel {
    enum PreviewState: Equatable {
        case loading
        case sections([EntityPreview.Section])
        case image(Data)
        case text(String)
        case unsupported(Int)
        case tooLarge(Int64)
        case unavailable
        case failed(String)
    }

    let entityKey: String
    let displayName: String

    private let metadata: EntityMetadata
    private let recordKinds: [String: any LibraryRecordPreviewing]
    private let maxContentBytes: Int64
    private let loadBytes: @Sendable (any EntityContentMetadata, String) async throws -> Data

    private(set) var state: PreviewState = .loading
    private var cached: Data?

    static let maxTextCharacters = 200_000
    static let defaultMaxContentBytes: Int64 = 10 * 1024 * 1024

    init(
        entityKey: String,
        displayName: String,
        metadata: EntityMetadata,
        recordKinds: [String: any LibraryRecordPreviewing],
        maxContentBytes: Int64,
        loadBytes: @escaping @Sendable (any EntityContentMetadata, String) async throws -> Data
    ) {
        self.entityKey = entityKey
        self.displayName = displayName
        self.metadata = metadata
        self.recordKinds = recordKinds
        self.maxContentBytes = maxContentBytes
        self.loadBytes = loadBytes
    }

    static func live(
        session: AuthenticatedSession,
        entityKey: String,
        displayName: String,
        metadata: EntityMetadata
    ) -> EntryContentModel {
        EntryContentModel(
            entityKey: entityKey,
            displayName: displayName,
            metadata: metadata,
            recordKinds: LibraryRecordKinds.byScheme(),
            maxContentBytes: defaultMaxContentBytes,
            loadBytes: { content, key in
                try await EntityContent.pullBytes(
                    metadata: content,
                    entityKey: key,
                    deviceSecret: session.secretRef.get(),
                    clients: StaticClients(api: session.serverApiClient, core: session.serverCoreClient),
                    decryptor: Aes.shared,
                    onPartProcessed: {}
                )
            }
        )
    }

    var recordKind: (any LibraryRecordPreviewing)? {
        guard let scheme = SourceUri.scheme(entityKey) else { return nil }
        return recordKinds[scheme]
    }

    var canExport: Bool { recordKind != nil }

    func load() async {
        state = .loading
        do {
            state = classify(try await ensureLoaded())
        } catch let error as ContentError {
            state = error.state
        } catch {
            state = .failed(error.localizedDescription)
        }
    }

    func rawContent() async throws -> Data {
        try await ensureLoaded()
    }

    func exportedContent() async throws -> ExportedContent {
        guard let kind = recordKind else { throw ContentError.unavailable }
        return try kind.export(bytes: try await ensureLoaded())
    }

    private func ensureLoaded() async throws -> Data {
        if let cached { return cached }
        guard let content = metadata.content else { throw ContentError.unavailable }
        guard content.size <= maxContentBytes else { throw ContentError.tooLarge(content.size) }
        let bytes = try await loadBytes(content, entityKey)
        cached = bytes
        return bytes
    }

    private func classify(_ bytes: Data) -> PreviewState {
        if let kind = recordKind, let preview = try? kind.preview(bytes: bytes) {
            return .sections(preview.sections)
        }
        if UIImage(data: bytes) != nil { return .image(bytes) }
        if let text = Self.text(from: bytes) { return .text(text) }
        return .unsupported(bytes.count)
    }

    private static func text(from bytes: Data) -> String? {
        guard let string = String(data: bytes, encoding: .utf8) else { return nil }
        return string.count > maxTextCharacters ? String(string.prefix(maxTextCharacters)) : string
    }

    private enum ContentError: Error {
        case unavailable
        case tooLarge(Int64)

        var state: PreviewState {
            switch self {
            case .unavailable: .unavailable
            case .tooLarge(let size): .tooLarge(size)
            }
        }
    }
}

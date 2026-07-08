import Foundation
import UniformTypeIdentifiers

@MainActor
enum DropIngest {
    struct Outcome: Sendable, Equatable {
        var stored: [DropMetadata]
        var failures: [String]
    }

    static func ingest(items: [NSItemProvider], into inbox: DropInbox) async -> Outcome {
        var stored: [DropMetadata] = []
        var failures: [String] = []
        for provider in items {
            do {
                stored.append(try await store(provider, into: inbox))
            } catch {
                failures.append(error.localizedDescription)
            }
        }
        return Outcome(stored: stored, failures: failures)
    }

    private static func store(_ provider: NSItemProvider, into inbox: DropInbox) async throws -> DropMetadata {
        if provider.hasItemConformingToTypeIdentifier(UTType.url.identifier),
           !provider.hasItemConformingToTypeIdentifier(UTType.fileURL.identifier) {
            let url = try await loadURL(provider)
            return try await inbox.store(
                filename: appendingExtension(provider.suggestedName ?? "link", "url"),
                typeIdentifier: UTType.url.identifier,
                data: Data(url.absoluteString.utf8)
            )
        }
        if let typeIdentifier = dataTypeIdentifier(provider) {
            let file = try await loadFile(provider, typeIdentifier: typeIdentifier)
            defer { try? FileManager.default.removeItem(at: file.deletingLastPathComponent()) }
            return try await inbox.store(
                filename: provider.suggestedName ?? file.lastPathComponent,
                typeIdentifier: typeIdentifier,
                from: file
            )
        }
        if provider.hasItemConformingToTypeIdentifier(UTType.plainText.identifier) {
            let data = try await loadData(provider, typeIdentifier: UTType.plainText.identifier)
            return try await inbox.store(
                filename: appendingExtension(provider.suggestedName ?? "text", "txt"),
                typeIdentifier: UTType.plainText.identifier,
                data: data
            )
        }
        throw CocoaError(.featureUnsupported)
    }

    private static func dataTypeIdentifier(_ provider: NSItemProvider) -> String? {
        provider.registeredTypeIdentifiers.first { identifier in
            guard let type = UTType(identifier) else { return false }
            return type.conforms(to: .data) && !type.conforms(to: .url)
        }
    }

    private static func appendingExtension(_ name: String, _ fileExtension: String) -> String {
        (name as NSString).pathExtension.caseInsensitiveCompare(fileExtension) == .orderedSame
            ? name
            : "\(name).\(fileExtension)"
    }

    private static func loadFile(_ provider: NSItemProvider, typeIdentifier: String) async throws -> URL {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<URL, Error>) in
            provider.loadFileRepresentation(forTypeIdentifier: typeIdentifier) { url, error in
                guard let url else {
                    continuation.resume(throwing: error ?? CocoaError(.fileReadUnknown))
                    return
                }
                do {
                    let destination = FileManager.default.temporaryDirectory
                        .appendingPathComponent("drop-ingest-\(UUID().uuidString)", isDirectory: true)
                        .appendingPathComponent(url.lastPathComponent)
                    try FileManager.default.createDirectory(
                        at: destination.deletingLastPathComponent(),
                        withIntermediateDirectories: true
                    )
                    try FileManager.default.copyItem(at: url, to: destination)
                    continuation.resume(returning: destination)
                } catch {
                    continuation.resume(throwing: error)
                }
            }
        }
    }

    private static func loadURL(_ provider: NSItemProvider) async throws -> URL {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<URL, Error>) in
            _ = provider.loadObject(ofClass: URL.self) { url, error in
                if let url {
                    continuation.resume(returning: url)
                } else {
                    continuation.resume(throwing: error ?? CocoaError(.fileReadUnknown))
                }
            }
        }
    }

    private static func loadData(_ provider: NSItemProvider, typeIdentifier: String) async throws -> Data {
        try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<Data, Error>) in
            provider.loadDataRepresentation(forTypeIdentifier: typeIdentifier) { data, error in
                if let data {
                    continuation.resume(returning: data)
                } else {
                    continuation.resume(throwing: error ?? CocoaError(.fileReadUnknown))
                }
            }
        }
    }
}

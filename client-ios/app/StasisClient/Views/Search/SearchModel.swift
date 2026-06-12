import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class SearchModel {
    enum Status: Equatable, Sendable {
        case idle, running, completed
    }

    private let session: AuthenticatedSession
    private let search: any Search

    private(set) var status: Status = .idle
    private(set) var lastQuery: String = ""
    private(set) var result: SearchResult?
    private(set) var error: String?

    init(session: AuthenticatedSession, search: (any Search)? = nil) {
        self.session = session
        self.search = search ?? DefaultSearch(api: session.serverApiClient)
    }

    func clearError() { error = nil }

    func run(query: String, until: Date?) async {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            error = "Query cannot be empty."
            return
        }
        guard status != .running else { return }
        guard let regex = Self.buildRegex(query: trimmed) else {
            error = "Invalid query."
            return
        }
        status = .running
        error = nil
        lastQuery = trimmed
        result = nil
        do {
            result = try await search.search(query: regex, until: until)
        } catch {
            self.error = error.localizedDescription
        }
        status = .completed
    }

    static func buildRegex(query: String) -> NSRegularExpression? {
        if isPlainChars(query) {
            return try? NSRegularExpression(pattern: ".*\(query).*", options: .caseInsensitive)
        }
        if let regex = try? NSRegularExpression(pattern: query, options: .caseInsensitive) {
            return regex
        }
        let escaped = NSRegularExpression.escapedPattern(for: query)
        return try? NSRegularExpression(pattern: escaped, options: .caseInsensitive)
    }

    static func isPlainChars(_ value: String) -> Bool {
        guard !value.isEmpty else { return true }
        return value.unicodeScalars.allSatisfy { Self.plainCharacters.contains($0) }
    }

    private static let plainCharacters: CharacterSet = {
        var set = CharacterSet.alphanumerics
        set.insert(charactersIn: " _-")
        return set
    }()
}

extension SearchResult {
    var nonEmptyDefinitions: [DatasetDefinitionResult] {
        definitions.values.compactMap { $0 }.sorted { $0.definitionInfo < $1.definitionInfo }
    }
}

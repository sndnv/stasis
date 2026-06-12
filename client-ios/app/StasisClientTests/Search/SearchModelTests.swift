import Foundation
@testable import StasisClient
import StasisClientLib
import StasisClientLibTestSupport
import Testing

@MainActor
@Suite("SearchModel")
struct SearchModelTests {
    @Test("isPlainChars accepts alphanumerics, underscore, space and hyphen")
    func isPlainCharsAccepts() {
        #expect(SearchModel.isPlainChars("photos"))
        #expect(SearchModel.isPlainChars("My_Folder-2026 backup"))
        #expect(SearchModel.isPlainChars(""))
    }

    @Test("isPlainChars rejects regex metacharacters and punctuation")
    func isPlainCharsRejects() {
        #expect(SearchModel.isPlainChars(".*\\.jpg$") == false)
        #expect(SearchModel.isPlainChars("foo/bar") == false)
        #expect(SearchModel.isPlainChars("foo(bar)") == false)
    }

    @Test("buildRegex wraps plain queries as .*query.* and is case-insensitive")
    func buildRegexPlainWrap() throws {
        let regex = try #require(SearchModel.buildRegex(query: "photo"))
        #expect(regex.pattern == ".*photo.*")
        #expect(regex.options.contains(.caseInsensitive))
        #expect(matches(regex, "/path/Photo.jpg"))
    }

    @Test("buildRegex compiles a valid regex query unchanged")
    func buildRegexCompilesValid() throws {
        let regex = try #require(SearchModel.buildRegex(query: ".*\\.jpg$"))
        #expect(regex.pattern == ".*\\.jpg$")
        #expect(matches(regex, "/a/b/sunset.jpg"))
        #expect(matches(regex, "/a/b/sunset.txt") == false)
    }

    @Test("buildRegex falls back to literal when the regex fails to compile")
    func buildRegexLiteralFallback() throws {
        let regex = try #require(SearchModel.buildRegex(query: "[invalid"))
        #expect(regex.pattern == NSRegularExpression.escapedPattern(for: "[invalid"))
        #expect(matches(regex, "say [invalid please"))
    }

    @Test("run with empty query sets an error and does not invoke search")
    func runEmptyQueryError() async throws {
        let search = MockSearch()
        let model = try makeModel(search: search)

        await model.run(query: "   ", until: nil)

        #expect(model.error != nil)
        #expect(model.status == .idle)
        #expect(await search.calls.isEmpty)
    }

    @Test("run forwards the query and until date to the search backend")
    func runForwardsQueryAndUntil() async throws {
        let search = MockSearch()
        await search.setResult(SearchResult(definitions: [:]))
        let model = try makeModel(search: search)
        let until = Date(timeIntervalSince1970: 12_345)

        await model.run(query: "photo", until: until)

        let calls = await search.calls
        #expect(calls.count == 1)
        #expect(calls.first?.query.pattern == ".*photo.*")
        #expect(calls.first?.until == until)
        #expect(model.lastQuery == "photo")
        #expect(model.status == .completed)
        #expect(model.error == nil)
    }

    @Test("run trims whitespace from the query before invoking search")
    func runTrimsQuery() async throws {
        let search = MockSearch()
        let model = try makeModel(search: search)

        await model.run(query: "  photo  ", until: nil)

        #expect(model.lastQuery == "photo")
    }

    @Test("run stores the SearchResult returned by the backend")
    func runStoresResult() async throws {
        let search = MockSearch()
        let entryId = UUID()
        let definitionResult = DatasetDefinitionResult(
            definitionInfo: "Photos",
            entryId: entryId,
            entryCreated: Date(timeIntervalSince1970: 100),
            matches: ["/photos/sunset.jpg": .new]
        )
        await search.setResult(SearchResult(definitions: [UUID(): definitionResult]))
        let model = try makeModel(search: search)

        await model.run(query: "sunset", until: nil)

        #expect(model.result?.definitions.values.compactMap { $0 }.count == 1)
        #expect(model.result?.nonEmptyDefinitions.first?.entryId == entryId)
    }

    @Test("run surfaces an error when the search backend throws")
    func runSurfacesSearchError() async throws {
        let search = MockSearch()
        await search.setError(AccessDeniedFailure())
        let model = try makeModel(search: search)

        await model.run(query: "photo", until: nil)

        #expect(model.error != nil)
        #expect(model.status == .completed)
        #expect(model.result == nil)
    }

    @Test("clearError resets the error to nil")
    func clearErrorResets() async throws {
        let search = MockSearch()
        let model = try makeModel(search: search)
        await model.run(query: "   ", until: nil)
        #expect(model.error != nil)

        model.clearError()

        #expect(model.error == nil)
    }

    private func makeModel(search: any Search) throws -> SearchModel {
        let session = try TestSession.make()
        return SearchModel(session: session, search: search)
    }

    private func matches(_ regex: NSRegularExpression, _ input: String) -> Bool {
        let range = NSRange(input.startIndex..., in: input)
        return regex.firstMatch(in: input, options: [], range: range) != nil
    }
}

private actor MockSearch: Search {
    private(set) var calls: [(query: NSRegularExpression, until: Date?)] = []
    private var result: SearchResult = SearchResult(definitions: [:])
    private var error: (any Error)?

    func setResult(_ value: SearchResult) { result = value }
    func setError(_ value: (any Error)?) { error = value }

    func search(query: NSRegularExpression, until: Date?) async throws -> SearchResult {
        calls.append((query, until))
        if let error { throw error }
        return result
    }
}

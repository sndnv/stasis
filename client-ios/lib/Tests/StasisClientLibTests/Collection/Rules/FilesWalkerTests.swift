import Foundation
import Glob
@testable import StasisClientLib
import Testing

@Suite("FilesWalker")
struct FilesWalkerTests {
    @Test("FilterResult is empty when matches and failures are empty")
    func filterResultIsEmpty() {
        let empty = FilesWalker.FilterResult(matches: [:], failures: [:])
        #expect(empty.isEmpty())
    }

    @Test("filters files and directories based on provided matchers")
    func filtersBasedOnMatchers() throws {
        let (filesystem, _) = try createMockFileSystem(setup: .unix)
        let root = filesystem.root.path

        let rule1 = Rule(id: 0, operation: .include, source: "/", pattern: "*", definition: nil)
        let matcher1 = try makePattern("\(root)/root/parent-*/*-{a,b,c}")

        let rule2 = Rule(id: 1, operation: .exclude, source: "/", pattern: "*", definition: nil)
        let matcher2 = try makePattern("\(root)/root/parent-*/*-{d,e}")

        let matchers = [(rule1, matcher1), (rule2, matcher2)]

        var matchedSuccessful: [URL] = []
        let successfulResult = FilesWalker.filter(
            start: filesystem.resolve("root/parent-1"),
            matchers: matchers,
            onMatchIncluded: { matchedSuccessful.append($0) }
        )

        #expect(matchedSuccessful.map(\.path).sorted() == [
            "\(root)/root/parent-1/child-dir-a",
            "\(root)/root/parent-1/child-dir-b",
            "\(root)/root/parent-1/child-dir-c"
        ])

        #expect(!successfulResult.isEmpty())

        let includeMatches = successfulResult.matches[rule1]?.map(\.path).sorted() ?? []
        let excludeMatches = successfulResult.matches[rule2]?.map(\.path).sorted() ?? []

        #expect(includeMatches == [
            "\(root)/root/parent-1/child-dir-a",
            "\(root)/root/parent-1/child-dir-b",
            "\(root)/root/parent-1/child-dir-c"
        ])
        #expect(excludeMatches == [
            "\(root)/root/parent-1/child-dir-d",
            "\(root)/root/parent-1/child-dir-e"
        ])
        #expect(successfulResult.failures.isEmpty)

        var matchedFailed: [URL] = []
        let missingDir = filesystem.resolve("root/other")
        let failedResult = FilesWalker.filter(
            start: missingDir,
            matchers: matchers,
            onMatchIncluded: { matchedFailed.append($0) }
        )

        #expect(matchedFailed.isEmpty)
        #expect(!failedResult.isEmpty())
        #expect(failedResult.matches.isEmpty)
        #expect(failedResult.failures[missingDir] is NoSuchFileError)
    }

    @Test("skips excluded subtrees")
    func skipsExcludedSubtrees() throws {
        let (filesystem, _) = try createMockFileSystem(setup: .unix)
        let root = filesystem.root.path

        let rule1 = Rule(id: 0, operation: .include, source: "/", pattern: "*", definition: nil)
        let matcher1 = try makePattern("\(root)/root/parent-{0,1}/*-{a,b,c}/*")

        let rule2 = Rule(id: 1, operation: .exclude, source: "/", pattern: "*", definition: nil)
        let matcher2 = try makePattern("\(root)/root/parent-{0,1}/*-{c,d,e}")

        let matchers = [(rule1, matcher1), (rule2, matcher2)]

        let result = FilesWalker.filter(
            start: filesystem.resolve("root"),
            matchers: matchers,
            onMatchIncluded: { _ in }
        )

        #expect(!result.isEmpty())
        #expect(result.failures.isEmpty)

        let includedPaths = (result.matches[rule1] ?? []).map(\.path)
        for path in includedPaths {
            #expect(!path.contains("child-dir-c"))
            #expect(!path.contains("child-dir-d"))
            #expect(!path.contains("child-dir-e"))
        }

        let excludedPaths = (result.matches[rule2] ?? []).map(\.path).sorted()
        #expect(excludedPaths == [
            "\(root)/root/parent-0/child-dir-c",
            "\(root)/root/parent-0/child-dir-d",
            "\(root)/root/parent-0/child-dir-e",
            "\(root)/root/parent-1/child-dir-c",
            "\(root)/root/parent-1/child-dir-d",
            "\(root)/root/parent-1/child-dir-e"
        ])
    }

    private func makePattern(_ raw: String) throws -> Glob.Pattern {
        var options = Glob.Pattern.Options.default
        options.supportsBraceExpansion = true
        options.requiresExplicitLeadingPeriods = false
        return try Glob.Pattern(raw, options: options)
    }
}

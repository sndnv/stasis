import Foundation
import Glob

public enum FilesWalker {
    public struct FilterResult: Sendable {
        public let matches: [Rule: [URL]]
        public let failures: [URL: any Error]

        public init(matches: [Rule: [URL]], failures: [URL: any Error]) {
            self.matches = matches
            self.failures = failures
        }

        public func isEmpty() -> Bool {
            matches.isEmpty && failures.isEmpty
        }
    }

    public static func filter(
        start: URL,
        matchers: [(Rule, Glob.Pattern)],
        onMatchIncluded: (URL) -> Void
    ) -> FilterResult {
        let sortedMatchers = matchers.sorted { $0.0.id < $1.0.id }

        var collected: [Rule: [URL]] = [:]
        var failures: [URL: any Error] = [:]

        var isDirectory: ObjCBool = false
        guard FileManager.default.fileExists(atPath: start.path, isDirectory: &isDirectory) else {
            failures[start] = NoSuchFileError(path: start.path)
            return FilterResult(matches: collected, failures: failures)
        }

        func visit(_ url: URL, isDirectory: Bool) -> Bool {
            var lastMatchedRule: Rule?
            for (rule, matcher) in sortedMatchers where matcher.match(url.path) {
                lastMatchedRule = rule
                collected[rule, default: []].append(url)
            }
            switch lastMatchedRule?.operation {
            case .exclude where isDirectory:
                return false
            case .include:
                onMatchIncluded(url)
                return true
            default:
                return true
            }
        }

        if isDirectory.boolValue {
            _ = visit(start, isDirectory: true)
            guard let enumerator = FileManager.default.enumerator(
                at: start,
                includingPropertiesForKeys: [.isDirectoryKey],
                options: []
            ) else {
                return FilterResult(matches: collected, failures: failures)
            }
            while let next = enumerator.nextObject() as? URL {
                let values = try? next.resourceValues(forKeys: [.isDirectoryKey])
                let nextIsDirectory = values?.isDirectory ?? false
                let shouldDescend = visit(next, isDirectory: nextIsDirectory)
                if nextIsDirectory && !shouldDescend {
                    enumerator.skipDescendants()
                }
            }
        } else {
            _ = visit(start, isDirectory: false)
        }

        return FilterResult(matches: collected, failures: failures)
    }
}

public struct NoSuchFileError: Error, Equatable, CustomStringConvertible, LocalizedError {
    public let path: String

    public init(path: String) {
        self.path = path
    }

    public var description: String { "No such file: \(path)" }

    public var errorDescription: String? { description }
}

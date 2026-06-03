import Foundation
import Glob

public struct Specification: Sendable {
    public let entries: [URL: Entry]
    public let failures: [FailedMatch]

    public init(entries: [URL: Entry] = [:], failures: [FailedMatch] = []) {
        self.entries = entries
        self.failures = failures
    }

    public var explanation: [URL: [Entry.Explanation]] {
        entries.mapValues { $0.reason }
    }

    public var included: [URL] {
        Array(Set(includedEntries + includedParents))
    }

    public var excluded: [URL] { excludedEntries }

    public var unmatched: [(Rule, any Error)] {
        failures.map { ($0.rule, $0.failure) }
    }

    public var includedEntries: [URL] {
        entries.values.filter { $0.operation == .include }.map(\.file)
    }

    public var includedParents: [URL] {
        Array(Set(entries.values
            .filter { $0.operation == .include }
            .flatMap { Specification.collectRelativeParents(from: $0.directory, to: $0.file) }))
    }

    public var excludedEntries: [URL] {
        entries.values.filter { $0.operation == .exclude }.map(\.file)
    }

    public struct Entry: Sendable, Equatable {
        public let file: URL
        public let directory: URL
        public let operation: RuleOperation
        public let reason: [Explanation]

        public struct Explanation: Sendable, Equatable {
            public let operation: RuleOperation

            public init(operation: RuleOperation) {
                self.operation = operation
            }
        }
    }

    public struct RuleMatcher: Sendable {
        public let rule: Rule
        public let matcher: Glob.Pattern
    }

    public struct FailedMatch: Sendable {
        public let rule: Rule
        public let path: URL
        public let failure: any Error

        public init(rule: Rule, path: URL, failure: any Error) {
            self.rule = rule
            self.path = path
            self.failure = failure
        }
    }

    public static func empty() -> Specification { Specification() }

    public static func tracked(
        operation: OperationId,
        rules: [Rule],
        tracker: any BackupTracker
    ) -> Specification {
        Specification.build(rules: rules) { url in
            tracker.entityDiscovered(operation: operation, entity: url)
        }
    }

    public static func build(
        rules: [Rule],
        onMatchIncluded: (URL) -> Void
    ) -> Specification {
        let grouped = Dictionary(grouping: rules, by: \.directory)
        var allMatchers: [RuleMatcher] = []
        var spec = Specification.empty()

        for (groupedDirectory, groupedRules) in grouped {
            let (directory, matchers) = asMatchers(groupedDirectory, rules: groupedRules)

            let result = FilesWalker.filter(
                start: directory,
                matchers: matchers.map { ($0.rule, $0.matcher) },
                onMatchIncluded: onMatchIncluded
            )

            if result.isEmpty() {
                spec = Specification(
                    entries: spec.entries,
                    failures: spec.failures + groupedRules.map { rule in
                        FailedMatch(rule: rule, path: directory, failure: RuleMatchingFailure("Rule matched no files"))
                    }
                )
            } else {
                spec = spec.withMatches(result.matches, directory: directory)
                if let firstRule = groupedRules.first {
                    spec = spec.withFailures(rule: firstRule, failures: result.failures)
                }
            }

            allMatchers.append(contentsOf: matchers)
        }

        return spec.dropExcludedFailures(allMatchers)
    }

    private static func asMatchers(
        _ groupedDirectory: String,
        rules: [Rule]
    ) -> (URL, [RuleMatcher]) {
        let separator = "/"
        let ruleDirectory = groupedDirectory.hasSuffix(separator)
            ? groupedDirectory
            : groupedDirectory + separator

        let directory = URL(fileURLWithPath: ruleDirectory, isDirectory: true)

        var options = Glob.Pattern.Options.default
        options.supportsBraceExpansion = true
        options.requiresExplicitLeadingPeriods = false

        let matchers = rules.compactMap { rule -> RuleMatcher? in
            let raw = Specification.normalizeAlternation(ruleDirectory + rule.pattern)
            guard let pattern = try? Glob.Pattern(raw, options: options) else {
                return nil
            }
            return RuleMatcher(rule: rule, matcher: pattern)
        }

        return (directory, matchers)
    }

    private static func normalizeAlternation(_ raw: String) -> String {
        var result = ""
        result.reserveCapacity(raw.count)
        var braceDepth = 0
        for char in raw {
            switch char {
            case "{":
                braceDepth += 1
                result.append(char)
            case "}":
                if braceDepth > 0 { braceDepth -= 1 }
                result.append(char)
            case "|" where braceDepth > 0:
                result.append(",")
            default:
                result.append(char)
            }
        }
        return result
    }

    private func withMatches(_ matches: [Rule: [URL]], directory: URL) -> Specification {
        var collected = entries
        let ordered = matches
            .sorted { $0.key.id < $1.key.id }
            .flatMap { rule, files in files.map { (rule, $0) } }

        for (rule, file) in ordered {
            if let existing = collected[file] {
                collected[file] = Specification.Entry(
                    file: existing.file,
                    directory: existing.directory,
                    operation: rule.operation,
                    reason: existing.reason + [Entry.Explanation(operation: rule.operation)]
                )
            } else {
                collected[file] = Specification.Entry(
                    file: file,
                    directory: directory,
                    operation: rule.operation,
                    reason: [Entry.Explanation(operation: rule.operation)]
                )
            }
        }
        return Specification(entries: collected, failures: failures)
    }

    private func withFailures(rule: Rule, failures incoming: [URL: any Error]) -> Specification {
        let added = incoming.map { (path, failure) in
            FailedMatch(rule: rule, path: path, failure: failure)
        }
        return Specification(entries: entries, failures: failures + added)
    }

    private func dropExcludedFailures(_ matchers: [RuleMatcher]) -> Specification {
        let exclusions = matchers.filter { $0.rule.operation == .exclude }
        let kept = failures.filter { failure in
            !exclusions.contains { $0.matcher.match(failure.path.path) }
        }
        return Specification(entries: entries, failures: kept)
    }

    public static func collectRelativeParents(from: URL, to: URL) -> [URL] {
        let fromPath = from.standardizedPath
        let toPath = to.standardizedPath
        guard toPath.hasPrefix(fromPath) else { return [] }

        var collected: [URL] = []
        var current = to
        while current.standardizedPath != fromPath {
            let parent = current.deletingLastPathComponent()
            if parent.standardizedPath == current.standardizedPath { break }
            collected.append(parent)
            current = parent
        }
        return collected
    }
}

extension URL {
    fileprivate var standardizedPath: String {
        let path = self.path
        if path.count > 1 && path.hasSuffix("/") {
            return String(path.dropLast())
        }
        return path
    }
}

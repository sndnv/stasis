import Foundation
@testable import StasisClientLib
import Testing

@Suite("Specification")
struct SpecificationTests {
    @Test("supports creation based on rules")
    func supportsCreationBasedOnRules() throws {
        let (filesystem, objects) = try createMockFileSystem(setup: .unix)
        let root = filesystem.root.path

        #expect(objects.filesPerDir > 0)
        #expect(objects.rootDirs > 0)
        #expect(objects.nestedDirs > 0)

        let rule1 = Rule(id: 1, operation: .include, directory: root, pattern: "?", definition: nil)
        let rule2 = Rule(id: 2, operation: .exclude, directory: root, pattern: "[a-z]", definition: nil)
        let rule3 = Rule(id: 3, operation: .exclude, directory: root, pattern: "{0|1}", definition: nil)
        let rule4 = Rule(id: 4, operation: .include, directory: root, pattern: "root-dir-?/*", definition: nil)
        let rule5 = Rule(id: 5, operation: .include, directory: "\(root)/root", pattern: "**/child-*[a-c]/a", definition: nil)
        let rule6 = Rule(id: 6, operation: .exclude, directory: "\(root)/root", pattern: "parent-0/**", definition: nil)
        let rule7 = Rule(id: 7, operation: .exclude, directory: "\(root)/root", pattern: "**/q", definition: nil)

        let azRangeSize = 26
        let zeroOneListSize = 2
        let rootDirsFiles = objects.rootDirs * objects.filesPerDir
        let acChildFiles = objects.nestedParentDirs * 3 * 1
        let qFiles = objects.nestedDirs * 1

        let work = 1
        let workRoot = 1
        let rootDirs = objects.rootDirs
        let acChildDirs = objects.nestedParentDirs * 3 + objects.nestedParentDirs
        let parent0Dirs = objects.nestedChildDirsPerParent

        let cases: [(Rule, RuleExpectation)] = [
            (rule1, RuleExpectation(excluded: 0, included: objects.filesPerDir + work, root: work)),
            (rule2, RuleExpectation(excluded: azRangeSize, included: 0, root: 0)),
            (rule3, RuleExpectation(excluded: zeroOneListSize, included: 0, root: 0)),
            (rule4, RuleExpectation(excluded: 0, included: rootDirsFiles + rootDirs + workRoot, root: workRoot + rootDirs)),
            (rule5, RuleExpectation(
                excluded: 0,
                included: acChildFiles + acChildDirs + workRoot,
                root: workRoot + acChildDirs
            )),
            (rule6, RuleExpectation(excluded: parent0Dirs, included: 0, root: 0)),
            (rule7, RuleExpectation(excluded: qFiles, included: 0, root: 0))
        ]

        for (rule, expectation) in cases {
            var matchesIncluded = 0
            let spec = Specification.build(rules: [rule]) { _ in matchesIncluded += 1 }
            #expect(spec.excluded.count == expectation.excluded, "rule \(rule.id) excluded")
            #expect(spec.included.count == expectation.included, "rule \(rule.id) included")
            #expect(matchesIncluded == expectation.included - expectation.root, "rule \(rule.id) onMatchIncluded")
        }

        let spec = Specification.build(rules: cases.map(\.0)) { _ in }

        #expect(spec.unmatched.isEmpty)
        #expect(spec.entries.count < objects.total)

        let includedFromRoot = objects.filesPerDir
        let excludedFromRoot = azRangeSize + zeroOneListSize
        let includedUnderRootDirs = rootDirsFiles + rootDirs
        let includedUnderChildDirs = acChildFiles + acChildDirs
        let excludedUnderParent0 = parent0Dirs
        let excludedQFiles = qFiles

        let overlappingQFilesInParent0 = objects.nestedChildDirsPerParent
        let overlappingAcChildFilesInParent0 = includedUnderChildDirs / objects.nestedParentDirs
        let overlappingEntriesInParent0 = overlappingQFilesInParent0 + overlappingAcChildFilesInParent0

        let entriesUnderRoot = includedFromRoot + work
        let entriesUnderRootDirs = includedUnderRootDirs + workRoot
        let entriesUnderNestedDirs =
            includedUnderChildDirs + excludedUnderParent0 + excludedQFiles - overlappingEntriesInParent0

        let totalEntries = entriesUnderRoot + entriesUnderRootDirs + entriesUnderNestedDirs
        let excludedEntries = excludedFromRoot + excludedUnderParent0 + excludedQFiles - overlappingQFilesInParent0
        let includedEntries = totalEntries - excludedEntries

        #expect(Set(spec.included + spec.excluded).count == totalEntries)
        #expect(spec.excluded.count == excludedEntries)
        #expect(spec.included.count == includedEntries)
    }

    @Test("provides list of unmatched rules")
    func providesListOfUnmatchedRules() throws {
        let (filesystem, _) = try createMockFileSystem(setup: .empty)
        let root = filesystem.root.path

        let rule1 = Rule(id: 1, operation: .include, directory: "\(root)/test/", pattern: "**", definition: nil)
        let rule2 = Rule(id: 2, operation: .include, directory: root, pattern: "missing-test-file", definition: nil)

        let spec = Specification.build(rules: [rule1, rule2]) { _ in }

        #expect(spec.unmatched.count == 2)
        let sorted = spec.failures.sorted { $0.rule.id < $1.rule.id }
        #expect(sorted[0].rule == rule1)
        #expect(sorted[0].failure is NoSuchFileError)
        #expect(sorted[1].rule == rule2)
        #expect(sorted[1].failure is RuleMatchingFailure)
        #expect(spec.entries.isEmpty)
    }

    @Test("provides a reason for including/excluding each file")
    func providesReasonForEachFile() throws {
        let (filesystem, _) = try createMockFileSystem(
            setup: .unix.with(chars: FileSystemSetup.alphaNumericChars, nestedParentDirs: 0)
        )
        let root = filesystem.root.path

        let rule1 = Rule(id: 1, operation: .include, directory: root, pattern: "?", definition: nil)
        let rule2 = Rule(id: 2, operation: .exclude, directory: root, pattern: "a", definition: nil)
        let rule3 = Rule(id: 3, operation: .exclude, directory: root, pattern: "b", definition: nil)
        let rule4 = Rule(id: 4, operation: .exclude, directory: root, pattern: "c", definition: nil)
        let rule5 = Rule(id: 5, operation: .include, directory: root, pattern: "[c-f]", definition: nil)

        let rules = [rule1, rule2, rule3, rule4, rule5]
        let spec = Specification.build(rules: rules) { _ in }

        #expect(spec.unmatched.isEmpty)

        func entry(_ name: String) -> Specification.Entry? {
            spec.entries[filesystem.resolve(name)]
        }

        let fileA = try #require(entry("a"))
        let fileB = try #require(entry("b"))
        let fileC = try #require(entry("c"))
        let fileD = try #require(entry("d"))
        let fileE = try #require(entry("e"))
        let fileF = try #require(entry("f"))

        #expect(fileA.operation == .exclude)
        #expect(fileA.reason == [.init(operation: .include), .init(operation: .exclude)])

        #expect(fileB.operation == .exclude)
        #expect(fileB.reason == [.init(operation: .include), .init(operation: .exclude)])

        #expect(fileC.operation == .include)
        #expect(fileC.reason == [
            .init(operation: .include),
            .init(operation: .exclude),
            .init(operation: .include)
        ])

        #expect(fileD.operation == .include)
        #expect(fileD.reason == [.init(operation: .include), .init(operation: .include)])

        #expect(fileE.operation == .include)
        #expect(fileE.reason == [.init(operation: .include), .init(operation: .include)])

        #expect(fileF.operation == .include)
        #expect(fileF.reason == [.init(operation: .include), .init(operation: .include)])
    }

    @Test("creates an empty spec if no rules are provided")
    func emptySpecForEmptyRules() {
        let spec = Specification.build(rules: []) { _ in }
        #expect(spec.entries.isEmpty)
        #expect(spec.failures.isEmpty)
    }

    @Test("handles matching failures")
    func handlesMatchingFailures() throws {
        let (filesystem, _) = try createMockFileSystem(setup: .empty)
        let root = filesystem.root.path

        let rule1 = Rule(id: 1, operation: .include, directory: "\(root)/missing-dir", pattern: "*", definition: nil)

        let spec = Specification.build(rules: [rule1]) { _ in }

        #expect(spec.unmatched.count == 1)
        #expect(spec.failures[0].rule == rule1)
        #expect(spec.failures[0].failure is NoSuchFileError)
        #expect(spec.entries.isEmpty)
    }

    @Test("supports collecting parent directories")
    func collectsParentDirectories() throws {
        let (filesystem, _) = try createMockFileSystem(setup: .unix)
        let root = filesystem.root

        let case1 = Specification.collectRelativeParents(
            from: URL(fileURLWithPath: "/"),
            to: root.appendingPathComponent("root/parent-0/child-dir-a/a")
        ).map(\.path).sorted()
        let expectedComponents1 = root.pathComponents.dropFirst()
        var case1Expected = ["/", "/\(expectedComponents1.first!)"]
        var accum = "/\(expectedComponents1.first!)"
        for component in expectedComponents1.dropFirst() {
            accum += "/\(component)"
            case1Expected.append(accum)
        }
        case1Expected.append("\(root.path)/root")
        case1Expected.append("\(root.path)/root/parent-0")
        case1Expected.append("\(root.path)/root/parent-0/child-dir-a")
        #expect(case1 == case1Expected.sorted())

        let case2 = Specification.collectRelativeParents(
            from: filesystem.resolve("root/parent-0"),
            to: filesystem.resolve("root/parent-0/child-dir-a/a")
        ).map(\.path).sorted()
        #expect(case2 == [
            "\(root.path)/root/parent-0",
            "\(root.path)/root/parent-0/child-dir-a"
        ].sorted())

        let case3 = Specification.collectRelativeParents(
            from: filesystem.resolve("root/parent-0/child-dir-a"),
            to: filesystem.resolve("root/parent-0/child-dir-a/a")
        ).map(\.path)
        #expect(case3 == ["\(root.path)/root/parent-0/child-dir-a"])

        let case4 = Specification.collectRelativeParents(
            from: filesystem.resolve("root/parent-0/child-dir-a"),
            to: filesystem.resolve("root/parent-0/child-dir-a")
        )
        #expect(case4.isEmpty)
    }

    @Test("handles mismatched parent directories")
    func handlesMismatchedParents() throws {
        let (filesystem, _) = try createMockFileSystem(setup: .unix)
        let result = Specification.collectRelativeParents(
            from: filesystem.resolve("root/parent-0"),
            to: filesystem.resolve("root/parent-1/child-dir-b/c")
        )
        #expect(result.isEmpty)
    }
}

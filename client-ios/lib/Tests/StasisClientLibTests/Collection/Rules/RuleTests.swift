import Foundation
@testable import StasisClientLib
import Testing

@Suite("Rule")
struct RuleTests {
    @Test("renders as string")
    func renderAsString() {
        let rule1 = Rule(
            id: 1,
            operation: .include,
            directory: "/work",
            pattern: "?",
            definition: nil
        )
        let definition2 = UUID()
        let rule2 = Rule(
            id: 2,
            operation: .exclude,
            directory: "/work",
            pattern: "[a-z]",
            definition: definition2
        )
        let rule3 = Rule(
            id: 3,
            operation: .include,
            directory: "/work",
            pattern: "{0|1}",
            definition: nil
        )
        let rule4 = Rule(
            id: 4,
            operation: .exclude,
            directory: "/work/root",
            pattern: "**/q",
            definition: nil
        )

        #expect(rule1.asString() == "+ /work ?")
        #expect(rule2.asString() == "- /work [a-z] (\(definition2))")
        #expect(rule3.asString() == "+ /work {0|1}")
        #expect(rule4.asString() == "- /work/root **/q")
    }
}

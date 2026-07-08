import Foundation
@testable import StasisClient
import Testing

@Suite("HelpTopic")
struct HelpTopicTests {
    @Test("every topic has a non-empty help message")
    func everyTopicHasMessage() {
        for topic in HelpTopic.allCases {
            #expect(!topic.message.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
    }

    @Test("messages avoid platform-foreign wording")
    func messagesAreIosIdiomatic() {
        for topic in HelpTopic.allCases {
            let lowercased = topic.message.lowercased()
            #expect(!lowercased.contains("long-press"))
            #expect(!lowercased.contains("long press"))
        }
    }
}

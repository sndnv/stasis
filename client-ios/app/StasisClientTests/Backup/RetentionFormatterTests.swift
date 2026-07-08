import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("RetentionFormatter")
struct RetentionFormatterTests {
    private func retention(_ policy: DatasetDefinition.Retention.Policy, _ seconds: Int64) -> DatasetDefinition.Retention {
        DatasetDefinition.Retention(policy: policy, duration: SecondsDuration(seconds))
    }

    @Test("shortLabel renders the policy without duration")
    func shortLabel() {
        #expect(RetentionFormatter.shortLabel(retention(.all, 42)) == "All")
        #expect(RetentionFormatter.shortLabel(retention(.latestOnly, 42)) == "Latest only")
        #expect(RetentionFormatter.shortLabel(retention(.atMost(versions: 3), 42)) == "3")
    }

    @Test("description renders the policy and duration")
    func description() {
        let duration = StatusFormatters.duration(SecondsDuration(42))
        #expect(RetentionFormatter.description(retention(.all, 42)) == "All, \(duration)")
        #expect(RetentionFormatter.description(retention(.latestOnly, 42)) == "Latest only, \(duration)")
        #expect(RetentionFormatter.description(retention(.atMost(versions: 2), 42)) == "At most 2, \(duration)")
    }
}

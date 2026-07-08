import Foundation
import StasisClientLib

enum RetentionFormatter {
    static func shortLabel(_ retention: DatasetDefinition.Retention) -> String {
        switch retention.policy {
        case .all: "All"
        case .latestOnly: "Latest only"
        case .atMost(let versions): "\(versions)"
        }
    }

    static func description(_ retention: DatasetDefinition.Retention) -> String {
        let policy: String
        switch retention.policy {
        case .all: policy = "All"
        case .latestOnly: policy = "Latest only"
        case .atMost(let versions): policy = "At most \(versions)"
        }
        return "\(policy), \(StatusFormatters.duration(retention.duration))"
    }
}

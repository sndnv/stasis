import Foundation
@testable import StasisClientLib

final class MockServerTracker: ServerTracker {
    enum Statistic: String, CaseIterable, Sendable {
        case serverReachable = "ServerReachable"
        case serverUnreachable = "ServerUnreachable"
    }

    private let counter = StatsCounter<Statistic>()

    var statistics: [Statistic: Int] { counter.snapshot }

    func reachable(server: String) async { counter.increment(.serverReachable) }
    func unreachable(server: String) async { counter.increment(.serverUnreachable) }
}

import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@MainActor
@Suite("CollectedAnalyticsModel")
struct CollectedAnalyticsModelTests {
    @Test("starts in .loading and transitions to .loaded after a successful state fetch")
    func loadSucceeds() async {
        let entry = AnalyticsEntry.collected(.init(app: TestApplicationInformation()))
        let collector = StubCollector(stateResult: .success(entry))
        let model = CollectedAnalyticsModel(collector: collector)
        #expect(model.state == .loading)

        await model.load()

        guard case let .loaded(loaded) = model.state else {
            Issue.record("expected .loaded, got \(model.state)")
            return
        }
        #expect(loaded == entry)
    }

    @Test("transitions to .failed when the collector reports an error")
    func loadFails() async {
        let collector = StubCollector(stateResult: .failure(TestError.boom))
        let model = CollectedAnalyticsModel(collector: collector)

        await model.load()

        guard case .failed = model.state else {
            Issue.record("expected .failed, got \(model.state)")
            return
        }
    }

    @Test("send toggles sendInProgress and forwards to the collector")
    func sendForwards() async {
        let collector = StubCollector(stateResult: .success(.collected(.init(app: TestApplicationInformation()))))
        let model = CollectedAnalyticsModel(collector: collector)

        await model.send()

        #expect(model.sendInProgress == false)
        #expect(await collector.sendCount == 1)
    }
}

private actor StubCollector: AnalyticsCollector {
    private let stateResult: Result<AnalyticsEntry, Error>
    private(set) var sendCount: Int = 0

    init(stateResult: Result<AnalyticsEntry, Error>) {
        self.stateResult = stateResult
    }

    func recordEvent(name: String, attributes: [String: String]) async {}
    func recordFailure(message: String) async {}
    func state() async -> Result<AnalyticsEntry, any Error> { stateResult }
    func send() async { sendCount += 1 }
    var persistence: (any AnalyticsPersistence)? { get async { nil } }
}

private struct TestApplicationInformation: ApplicationInformation {
    let name: String = "test"
    let version: String = "0.0.0"
    let buildTime: Int64 = 0
}

private enum TestError: Error { case boom }

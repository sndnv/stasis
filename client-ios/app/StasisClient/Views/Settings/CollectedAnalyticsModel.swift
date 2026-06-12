import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class CollectedAnalyticsModel {
    enum LoadState: Equatable {
        case loading
        case loaded(AnalyticsEntry)
        case failed(String)
    }

    private let collector: any AnalyticsCollector

    private(set) var state: LoadState = .loading
    private(set) var sendInProgress: Bool = false

    init(collector: any AnalyticsCollector) {
        self.collector = collector
    }

    func load() async {
        state = .loading
        switch await collector.state() {
        case .success(let entry): state = .loaded(entry)
        case .failure(let error): state = .failed(error.localizedDescription)
        }
    }

    func send() async {
        guard !sendInProgress else { return }
        sendInProgress = true
        await collector.send()
        sendInProgress = false
    }
}

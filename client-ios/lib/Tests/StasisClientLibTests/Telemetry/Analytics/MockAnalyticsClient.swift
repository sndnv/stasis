@testable import StasisClientLib

actor MockAnalyticsClient: AnalyticsClient {
    private let result: Result<Void, Error>
    private var sentCount: Int = 0
    private var lastEntry: AnalyticsEntry?

    init(result: Result<Void, Error> = .success(())) {
        self.result = result
    }

    func sendAnalyticsEntry(_ entry: AnalyticsEntry) async throws {
        switch result {
        case .success:
            sentCount += 1
            lastEntry = entry
        case .failure(let error):
            throw error
        }
    }

    var sent: Int { sentCount }
    var last: AnalyticsEntry? { lastEntry }
}

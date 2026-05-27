public protocol AnalyticsCollector: Sendable {
    func recordEvent(name: String, attributes: [String: String]) async
    func recordFailure(message: String) async
    func state() async -> Result<AnalyticsEntry, Error>
    func send() async

    var persistence: (any AnalyticsPersistence)? { get async }
}

extension AnalyticsCollector {
    public func recordEvent(name: String) async {
        await recordEvent(name: name, attributes: [:])
    }

    public func recordEvent<T: Sendable>(name: String, result: Result<T, Error>) async {
        let status: String = switch result {
        case .success: "success"
        case .failure: "failure"
        }
        await recordEvent(name: name, attributes: ["result": status])
    }

    public func recordFailure(_ error: any Error) async {
        let typeName = String(describing: type(of: error))
        await recordFailure(message: "\(typeName) - \(error.localizedDescription)")
    }
}

public struct NoOpAnalyticsCollector: AnalyticsCollector {
    public init() {}

    public func recordEvent(name: String, attributes: [String: String]) async {}
    public func recordFailure(message: String) async {}

    public func state() async -> Result<AnalyticsEntry, Error> {
        .success(.collected(AnalyticsEntry.Collected(app: NoApplicationInformation())))
    }

    public func send() async {}

    public var persistence: (any AnalyticsPersistence)? {
        get async { nil }
    }
}

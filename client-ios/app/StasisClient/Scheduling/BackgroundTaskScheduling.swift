import BackgroundTasks
import Foundation
import OSLog

public protocol BackgroundTaskScheduling: Sendable {
    func submitProcessing(identifier: String, earliestBeginDate: Date, requiresNetwork: Bool) throws
    func cancel(identifier: String)
}

public struct SystemBackgroundTaskScheduler: BackgroundTaskScheduling {
    private static let logger = Logger(subsystem: "stasis.client.ios", category: "BackgroundTaskScheduler")

    public init() {}

    public func submitProcessing(identifier: String, earliestBeginDate: Date, requiresNetwork: Bool) throws {
        let request = BGProcessingTaskRequest(identifier: identifier)
        request.earliestBeginDate = earliestBeginDate
        request.requiresNetworkConnectivity = requiresNetwork
        try BGTaskScheduler.shared.submit(request)
    }

    public func cancel(identifier: String) {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: identifier)
    }
}

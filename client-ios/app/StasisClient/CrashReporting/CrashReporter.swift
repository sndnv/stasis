import Foundation
import MetricKit
import StasisClientLib

final class CrashReporter: NSObject, MXMetricManagerSubscriber {
    private let analyticsCollector: any AnalyticsCollector
    private let preferencesSuiteName: String
    private let maxFrames: Int

    init(analyticsCollector: any AnalyticsCollector, preferencesSuiteName: String, maxFrames: Int) {
        self.analyticsCollector = analyticsCollector
        self.preferencesSuiteName = preferencesSuiteName
        self.maxFrames = maxFrames
        super.init()
    }

    func register() {
        MXMetricManager.shared.add(self)
    }

    func didReceive(_ payloads: [MXMetricPayload]) {}

    func didReceive(_ payloads: [MXDiagnosticPayload]) {
        let messages = payloads
            .flatMap { $0.crashDiagnostics ?? [] }
            .map { summary(from: $0).message() }

        guard !messages.isEmpty else { return }

        if let latest = messages.last {
            UserDefaults(suiteName: preferencesSuiteName)?.setLastCrashSummary(latest)
        }

        let collector = analyticsCollector
        Task {
            for message in messages {
                await collector.recordFailure(message: message)
            }
        }
    }

    private func summary(from diagnostic: MXCrashDiagnostic) -> CrashSummary {
        CrashSummary(
            exceptionType: diagnostic.exceptionType?.intValue,
            exceptionCode: diagnostic.exceptionCode?.intValue,
            signal: diagnostic.signal?.intValue,
            terminationReason: diagnostic.terminationReason,
            topFrames: CallStackTreeParser.topFrames(
                fromJSON: diagnostic.callStackTree.jsonRepresentation(),
                limit: maxFrames
            )
        )
    }
}

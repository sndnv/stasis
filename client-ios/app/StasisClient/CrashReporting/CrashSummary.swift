import StasisClientLib

struct CrashSummary: Equatable, Sendable {
    let exceptionType: Int?
    let exceptionCode: Int?
    let signal: Int?
    let terminationReason: String?
    let topFrames: [String]

    func message() -> String {
        var parts: [String] = []
        if let exceptionType { parts.append("exception \(exceptionType)") }
        if let signal { parts.append("signal \(signal)") }
        if let exceptionCode { parts.append("code \(exceptionCode)") }
        if let terminationReason, !terminationReason.isEmpty { parts.append(terminationReason) }

        let header = parts.isEmpty ? "Unrecoverable error" : parts.joined(separator: " · ")
        let body = topFrames.isEmpty ? header : "\(header) — \(topFrames.joined(separator: " ← "))"
        return AnalyticsEntry.Failure.anonymize(body)
    }
}

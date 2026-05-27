import Foundation

private let analyticsRuntimeId: String = UUID().uuidString

public enum AnalyticsEntry: Sendable, Equatable, Hashable {
    case asJson(AsJson)
    case collected(Collected)

    public var runtime: RuntimeInformation {
        switch self {
        case .asJson(let entry): entry.runtime
        case .collected(let entry): entry.runtime
        }
    }

    public var events: [Event] {
        switch self {
        case .asJson(let entry): entry.events
        case .collected(let entry): entry.events
        }
    }

    public var failures: [Failure] {
        switch self {
        case .asJson(let entry): entry.failures
        case .collected(let entry): entry.failures
        }
    }

    public var created: Date {
        switch self {
        case .asJson(let entry): entry.created
        case .collected(let entry): entry.created
        }
    }

    public var updated: Date {
        switch self {
        case .asJson(let entry): entry.updated
        case .collected(let entry): entry.updated
        }
    }

    public func asJson() -> AsJson {
        switch self {
        case .asJson(let entry): entry
        case .collected(let entry):
            AsJson(
                entryType: "collected",
                runtime: entry.runtime,
                events: entry.events,
                failures: entry.failures,
                created: entry.created,
                updated: entry.updated
            )
        }
    }

    public func asCollected() -> Collected {
        switch self {
        case .asJson(let entry):
            Collected(
                runtime: entry.runtime,
                events: entry.events,
                failures: entry.failures,
                created: entry.created,
                updated: entry.updated
            )
        case .collected(let entry): entry
        }
    }

    public struct AsJson: Sendable, Equatable, Hashable, Codable {
        public let entryType: String
        public let runtime: RuntimeInformation
        public let events: [Event]
        public let failures: [Failure]
        public let created: Date
        public let updated: Date

        public init(
            entryType: String,
            runtime: RuntimeInformation,
            events: [Event],
            failures: [Failure],
            created: Date,
            updated: Date
        ) {
            self.entryType = entryType
            self.runtime = runtime
            self.events = events
            self.failures = failures
            self.created = created
            self.updated = updated
        }
    }

    public struct Collected: Sendable, Equatable, Hashable {
        public let runtime: RuntimeInformation
        public let events: [Event]
        public let failures: [Failure]
        public let created: Date
        public let updated: Date

        public init(
            runtime: RuntimeInformation,
            events: [Event],
            failures: [Failure],
            created: Date,
            updated: Date
        ) {
            self.runtime = runtime
            self.events = events
            self.failures = failures
            self.created = created
            self.updated = updated
        }

        public init(app: any ApplicationInformation) {
            let now = Date()
            self.init(
                runtime: RuntimeInformation(app: app),
                events: [],
                failures: [],
                created: now,
                updated: now
            )
        }

        public func withEvent(name: String, attributes: [String: String]) -> Collected {
            let event = AnalyticsEntry.uniqueEvent(from: name, attributes: attributes)
            return Collected(
                runtime: runtime,
                events: events + [Event(id: events.count, event: event)],
                failures: failures,
                created: created,
                updated: Date()
            )
        }

        public func withFailure(message: String) -> Collected {
            Collected(
                runtime: runtime,
                events: events,
                failures: failures + [
                    Failure(message: Failure.anonymize(message), timestamp: Date())
                ],
                created: created,
                updated: Date()
            )
        }

        public func discardEvents() -> Collected {
            Collected(
                runtime: runtime,
                events: [],
                failures: failures,
                created: created,
                updated: Date()
            )
        }

        public func discardFailures() -> Collected {
            Collected(
                runtime: runtime,
                events: events,
                failures: [],
                created: created,
                updated: Date()
            )
        }
    }

    public struct Event: Sendable, Equatable, Hashable, Codable {
        public let id: Int
        public let event: String

        public init(id: Int, event: String) {
            self.id = id
            self.event = event
        }
    }

    public struct Failure: Sendable, Equatable, Hashable, Codable {
        public let message: String
        public let timestamp: Date

        public init(message: String, timestamp: Date) {
            self.message = message
            self.timestamp = timestamp
        }

        public static func anonymize(_ content: String) -> String {
            let pathStripped = content.replacingOccurrences(
                of: #"(?:[a-zA-Z]:\\|[\\/])(?:[\w\-. ]+[\\/])*[\w\-. ]+"#,
                with: " *CONTENT_REMOVED* ",
                options: .regularExpression
            )
            return pathStripped.replacingOccurrences(
                of: #"\s\s+"#,
                with: " ",
                options: .regularExpression
            ).trimmingCharacters(in: .whitespaces)
        }
    }

    public struct RuntimeInformation: Sendable, Equatable, Hashable, Codable {
        public let id: String
        public let app: String
        public let jre: String
        public let os: String

        public init(id: String, app: String, jre: String, os: String) {
            self.id = id
            self.app = app
            self.jre = jre
            self.os = os
        }

        public init(app: any ApplicationInformation) {
            self.init(
                id: analyticsRuntimeId,
                app: app.asString(),
                jre: "none",
                os: AnalyticsEntry.osDescriptor()
            )
        }
    }

    static func uniqueEvent(from name: String, attributes: [String: String]) -> String {
        guard !attributes.isEmpty else { return name }
        let flattened = attributes
            .sorted { $0.key < $1.key }
            .map { "\($0.key)='\($0.value)'" }
            .joined(separator: ",")
        return "\(name){\(flattened)}"
    }

    static func osDescriptor() -> String {
        "\(osName);\(osVersion);\(osArch)"
    }

    static var osName: String {
        #if os(iOS)
        "iOS"
        #elseif os(macOS)
        "macOS"
        #else
        "unknown"
        #endif
    }

    static var osVersion: String {
        let version = ProcessInfo.processInfo.operatingSystemVersion
        return "\(version.majorVersion).\(version.minorVersion).\(version.patchVersion)"
    }

    static var osArch: String {
        #if arch(arm64)
        "arm64"
        #elseif arch(x86_64)
        "x86_64"
        #elseif arch(arm)
        "arm"
        #elseif arch(i386)
        "i386"
        #else
        "unknown"
        #endif
    }
}

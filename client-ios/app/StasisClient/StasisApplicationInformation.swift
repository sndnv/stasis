import Foundation
import StasisClientLib

public struct StasisApplicationInformation: ApplicationInformation {
    public let name: String
    public let version: String
    public let buildTime: Int64

    public init(name: String, version: String, buildTime: Int64) {
        self.name = name
        self.version = version
        self.buildTime = buildTime
    }

    public init(bundle: Bundle = .main, fallbackName: String = "stasis-client-ios") {
        let info = bundle.infoDictionary ?? [:]
        self.name = bundle.bundleIdentifier ?? fallbackName
        self.version = info["CFBundleShortVersionString"] as? String ?? "0.0.0"
        self.buildTime = (info["BuildTime"] as? NSNumber)?.int64Value ?? 0
    }
}

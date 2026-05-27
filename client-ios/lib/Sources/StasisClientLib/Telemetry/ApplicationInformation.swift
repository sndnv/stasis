public protocol ApplicationInformation: Sendable {
    var name: String { get }
    var version: String { get }
    var buildTime: Int64 { get }
}

extension ApplicationInformation {
    public func asString() -> String {
        "\(name);\(version);\(buildTime)"
    }
}

public struct NoApplicationInformation: ApplicationInformation {
    public let name: String = "none"
    public let version: String = "none"
    public let buildTime: Int64 = 0
    public init() {}
}

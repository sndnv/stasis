import Foundation

public struct SecondsDuration: Sendable, Equatable, Hashable, Codable {
    public let value: Int64

    public init(_ value: Int64) {
        self.value = value
    }

    public var duration: Duration {
        .seconds(value)
    }

    public init(from decoder: any Decoder) throws {
        let container = try decoder.singleValueContainer()
        self.value = try container.decode(Int64.self)
    }

    public func encode(to encoder: any Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(value)
    }
}

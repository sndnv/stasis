import Foundation

public struct LocalDateTime: Sendable, Equatable, Hashable, Codable {
    public let value: String
    public let components: DateComponents

    public init(_ value: String) {
        self.value = value
        self.components = Self.parse(value)
    }

    public init(from decoder: any Decoder) throws {
        let value = try decoder.singleValueContainer().decode(String.self)
        self.value = value
        self.components = Self.parse(value)
    }

    public func encode(to encoder: any Encoder) throws {
        var container = encoder.singleValueContainer()
        try container.encode(value)
    }

    private static func parse(_ value: String) -> DateComponents {
        let parts = value.split(separator: "T", maxSplits: 1)
        guard parts.count == 2 else { return DateComponents() }

        let date = parts[0].split(separator: "-")
        guard date.count == 3 else { return DateComponents() }

        let timeAndFraction = parts[1].split(separator: ".", maxSplits: 1)
        let time = timeAndFraction[0].split(separator: ":")

        var nanosecond: Int?
        if timeAndFraction.count == 2 {
            let fraction = String(timeAndFraction[1])
            if fraction.count <= 9, fraction.allSatisfy({ $0.isNumber }) {
                let padded = fraction + String(repeating: "0", count: 9 - fraction.count)
                nanosecond = Int(padded)
            }
        }

        return DateComponents(
            year: Int(date[0]),
            month: Int(date[1]),
            day: Int(date[2]),
            hour: time.count >= 1 ? Int(time[0]) : nil,
            minute: time.count >= 2 ? Int(time[1]) : nil,
            second: time.count >= 3 ? Int(time[2]) : 0,
            nanosecond: nanosecond
        )
    }
}

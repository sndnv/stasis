import Foundation
import StasisClientLib

enum StatusFormatters {
    static func shortId(_ id: UUID) -> String {
        String(id.uuidString.lowercased().prefix(8))
    }

    static func bytes(_ value: Int64) -> String {
        ByteCountFormatter.string(fromByteCount: value, countStyle: .file)
    }

    static func duration(_ value: SecondsDuration) -> String {
        value.duration.formatted(.units(allowed: [.days, .hours, .minutes, .seconds], width: .abbreviated))
    }
}

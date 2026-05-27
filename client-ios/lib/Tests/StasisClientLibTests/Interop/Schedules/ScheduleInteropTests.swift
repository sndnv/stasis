import Foundation
@testable import StasisClientLib
import Testing

@Suite("Schedule interop")
struct ScheduleInteropTests {
    @Test("decode and re-encode a schedule")
    func schedule() throws {
        try assert(
            domain: "schedules",
            resource: "Schedule",
            matches: Schedule(
                id: UUID(uuidString: "e4e1f2a7-ff2d-4e8f-b715-9bbe607684d4")!,
                info: "nightly backup",
                isPublic: true,
                start: LocalDateTime("2026-05-01T02:00:30"),
                interval: SecondsDuration(86400),
                created: Date(timeIntervalSince1970: 1777626000),
                updated: Date(timeIntervalSince1970: 1777712400)
            )
        )
    }
}

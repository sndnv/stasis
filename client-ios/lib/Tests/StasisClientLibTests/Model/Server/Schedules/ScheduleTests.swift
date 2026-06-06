import Foundation
@testable import StasisClientLib
import Testing

@Suite("Schedule.nextInvocation")
struct ScheduleNextInvocationTests {
    private let calendar: Calendar = {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "UTC")!
        return calendar
    }()

    @Test("returns start when it is in the future")
    func returnsStartWhenFuture() throws {
        let now = isoDate("2026-06-05T12:00:00Z")
        let start = "2026-06-05T12:00:25"
        let schedule = makeSchedule(start: start, intervalSeconds: 10)
        let next = try #require(schedule.nextInvocation(now: now, calendar: calendar))
        #expect(next == isoDate("2026-06-05T12:00:25Z"))
    }

    @Test("returns start unchanged when start equals now (boundary)")
    func returnsStartAtExactBoundary() throws {
        let now = isoDate("2026-06-05T12:00:00Z")
        let schedule = makeSchedule(start: "2026-06-05T12:00:00", intervalSeconds: 10)
        let next = try #require(schedule.nextInvocation(now: now, calendar: calendar))
        #expect(next == isoDate("2026-06-05T12:00:00Z"))
    }

    @Test("advances past start by one interval when start is recent (just past)")
    func advancesByOneIntervalForRecentPast() throws {
        let now = isoDate("2026-06-05T12:00:00Z")
        let schedule = makeSchedule(start: "2026-06-05T11:59:59", intervalSeconds: 10)
        let next = try #require(schedule.nextInvocation(now: now, calendar: calendar))
        #expect(next == isoDate("2026-06-05T12:00:09Z"))
    }

    @Test("advances past start by whole intervals (fractional offset)")
    func advancesPastStartFractionalOffset() throws {
        let now = isoDate("2026-06-05T12:00:00Z")
        let schedule = makeSchedule(start: "2026-06-05T11:59:35", intervalSeconds: 10)
        let next = try #require(schedule.nextInvocation(now: now, calendar: calendar))
        #expect(next == isoDate("2026-06-05T12:00:05Z"))
    }

    @Test("returns next interval when now is exactly on a multiple of interval past start")
    func returnsNextIntervalOnInvocationBoundary() throws {
        let now = isoDate("2026-06-05T12:00:30Z")
        let schedule = makeSchedule(start: "2026-06-05T12:00:00", intervalSeconds: 10)
        let next = try #require(schedule.nextInvocation(now: now, calendar: calendar))
        #expect(next == isoDate("2026-06-05T12:00:40Z"))
    }

    @Test("treats zero-second interval as one-second interval")
    func treatsZeroIntervalAsOne() throws {
        let now = isoDate("2026-06-05T12:00:00Z")
        let schedule = makeSchedule(start: "2026-06-05T11:59:55", intervalSeconds: 0)
        let next = try #require(schedule.nextInvocation(now: now, calendar: calendar))
        #expect(next == isoDate("2026-06-05T12:00:01Z"))
    }

    @Test("lastInvocation returns nil before start")
    func lastInvocationBeforeStart() {
        let now = isoDate("2026-06-05T12:00:00Z")
        let schedule = makeSchedule(start: "2026-06-05T15:00:00", intervalSeconds: 3600)
        #expect(schedule.lastInvocation(now: now, calendar: calendar) == nil)
    }

    @Test("lastInvocation returns start when now equals start")
    func lastInvocationAtStart() throws {
        let now = isoDate("2026-06-05T12:00:00Z")
        let schedule = makeSchedule(start: "2026-06-05T12:00:00", intervalSeconds: 3600)
        let last = try #require(schedule.lastInvocation(now: now, calendar: calendar))
        #expect(last == isoDate("2026-06-05T12:00:00Z"))
    }

    @Test("lastInvocation rounds down to the most recent past invocation")
    func lastInvocationPastInterval() throws {
        let now = isoDate("2026-06-05T12:30:00Z")
        let schedule = makeSchedule(start: "2026-06-05T10:00:00", intervalSeconds: 3600)
        let last = try #require(schedule.lastInvocation(now: now, calendar: calendar))
        #expect(last == isoDate("2026-06-05T12:00:00Z"))
    }

    private func makeSchedule(start: String, intervalSeconds: Int64) -> Schedule {
        Schedule(
            id: UUID(),
            info: "test",
            isPublic: true,
            start: LocalDateTime(start),
            interval: SecondsDuration(intervalSeconds),
            created: Date(timeIntervalSince1970: 0),
            updated: Date(timeIntervalSince1970: 0)
        )
    }

    private func isoDate(_ value: String) -> Date {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)!
    }
}

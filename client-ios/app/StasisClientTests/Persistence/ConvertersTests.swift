import Foundation
@testable import StasisClient
import StasisClientLib
import Testing

@Suite("Converters")
struct ConvertersTests {
    @Test("rule round-trips through entity")
    func ruleRoundTrip() throws {
        let rule = Rule(
            id: 42,
            operation: .exclude,
            source: "/Users/test/docs",
            pattern: "**/*.log",
            definition: UUID()
        )
        let recovered = try rule.toEntity().toRule()
        #expect(recovered == rule)
    }

    @Test("schedule round-trips through entity")
    func scheduleRoundTrip() {
        let schedule = Schedule(
            id: UUID(),
            info: "nightly backup",
            isPublic: false,
            start: LocalDateTime("2026-06-03T01:00:00"),
            interval: SecondsDuration(3600),
            created: Date(timeIntervalSince1970: 1_700_000_000),
            updated: Date(timeIntervalSince1970: 1_700_000_000)
        )
        let recovered = schedule.toLocalScheduleEntity().toSchedule()
        #expect(recovered.id == schedule.id)
        #expect(recovered.info == schedule.info)
        #expect(recovered.start == schedule.start)
        #expect(recovered.interval == schedule.interval)
        #expect(recovered.created == schedule.created)
    }

    @Test("backup assignment round-trips through entity")
    func backupAssignmentRoundTrip() throws {
        let assignment: OperationScheduleAssignment = .backup(
            schedule: UUID(),
            definition: UUID(),
            entities: [URL(fileURLWithPath: "/tmp/a"), URL(fileURLWithPath: "/tmp/b")]
        )
        let active = ActiveSchedule(id: 7, assignment: assignment)
        let recovered = try active.toEntity().toActiveSchedule()
        #expect(recovered.assignment == assignment)
    }

    @Test("non-backup assignments round-trip with nil data")
    func nonBackupRoundTrip() throws {
        for assignment: OperationScheduleAssignment in [
            .expiration(schedule: UUID()),
            .validation(schedule: UUID()),
            .keyRotation(schedule: UUID())
        ] {
            let entity = try ActiveSchedule(id: 0, assignment: assignment).toEntity()
            #expect(entity.data == nil)
            #expect(try entity.toActiveSchedule().assignment == assignment)
        }
    }

    @Test("rejects unknown assignment type")
    func rejectsUnknownType() {
        let entity = ActiveScheduleEntity(id: 1, schedule: UUID(), type: "bogus")
        #expect(throws: ConverterError.self) { _ = try entity.toActiveSchedule() }
    }

    @Test("backup assignment encodes to exact wire-format keys")
    func backupAssignmentWireFormat() throws {
        let schedule = UUID()
        let definition = UUID()
        let assignment: OperationScheduleAssignment = .backup(
            schedule: schedule,
            definition: definition,
            entities: [URL(fileURLWithPath: "/tmp/a"), URL(fileURLWithPath: "/tmp/b")]
        )
        let entity = try ActiveSchedule(id: 0, assignment: assignment).toEntity()

        #expect(entity.type == "backup")
        let raw = try #require(entity.data)
        let json = try JSONSerialization.jsonObject(with: Data(raw.utf8)) as? [String: Any]
        #expect(json?["definition"] as? String == definition.uuidString)
        #expect(json?["entities"] as? [String] == ["/tmp/a", "/tmp/b"])
    }

    @Test("non-backup assignments encode to their expected type strings")
    func nonBackupAssignmentWireFormat() throws {
        let schedule = UUID()
        for (assignment, expected): (OperationScheduleAssignment, String) in [
            (.expiration(schedule: schedule), "expiration"),
            (.validation(schedule: schedule), "validation"),
            (.keyRotation(schedule: schedule), "key_rotation")
        ] {
            let entity = try ActiveSchedule(id: 0, assignment: assignment).toEntity()
            #expect(entity.type == expected)
            #expect(entity.data == nil)
        }
    }

    @Test("rule operation raw strings map to enum cases")
    func ruleOperationRawMapping() throws {
        #expect(RuleEntity.encode(.include) == "include")
        #expect(RuleEntity.encode(.exclude) == "exclude")
        #expect(try RuleEntity.decode("include") == .include)
        #expect(try RuleEntity.decode("exclude") == .exclude)
    }

    @Test("rejects unknown rule operation raw string")
    func ruleOperationRejectsUnknown() {
        #expect(throws: RuleEntityError.self) { _ = try RuleEntity.decode("bogus") }
    }
}

@Suite("ConverterError")
struct ConverterErrorTests {
    @Test("describes each case")
    func messages() {
        #expect(ConverterError.unexpectedAssignmentType("test").errorDescription == "Unexpected assignment type [test]")
        #expect(ConverterError.malformedAssignmentData("test a").errorDescription == "Malformed assignment data: test a")
    }
}

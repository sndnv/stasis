import Foundation
@testable import StasisClientLib
import Testing

@Suite("OperationScheduleAssignment")
struct OperationScheduleAssignmentTests {
    @Test("exposes the schedule id for each case")
    func exposesSchedule() {
        let backupSchedule = UUID()
        let expirationSchedule = UUID()
        let validationSchedule = UUID()
        let keyRotationSchedule = UUID()

        let backup = OperationScheduleAssignment.backup(
            schedule: backupSchedule,
            definition: UUID(),
            entities: [URL(fileURLWithPath: "/tmp/file")]
        )
        let expiration = OperationScheduleAssignment.expiration(schedule: expirationSchedule)
        let validation = OperationScheduleAssignment.validation(schedule: validationSchedule)
        let keyRotation = OperationScheduleAssignment.keyRotation(schedule: keyRotationSchedule)

        #expect(backup.schedule == backupSchedule)
        #expect(expiration.schedule == expirationSchedule)
        #expect(validation.schedule == validationSchedule)
        #expect(keyRotation.schedule == keyRotationSchedule)
    }
}

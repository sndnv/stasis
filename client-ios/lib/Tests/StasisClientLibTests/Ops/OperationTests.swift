import Foundation
@testable import StasisClientLib
import Testing

@Suite("OperationType")
struct OperationTypeTests {
    @Test("rawValue maps each case to its Pascal-case string")
    func rawValuesMatchAndroid() {
        #expect(OperationType.backup.rawValue == "Backup")
        #expect(OperationType.recovery.rawValue == "Recovery")
        #expect(OperationType.expiration.rawValue == "Expiration")
        #expect(OperationType.validation.rawValue == "Validation")
        #expect(OperationType.keyRotation.rawValue == "KeyRotation")
        #expect(OperationType.garbageCollection.rawValue == "GarbageCollection")
    }

    @Test("init(stringValue:) parses known names")
    func parsesKnownNames() throws {
        #expect(try OperationType(stringValue: "Backup") == .backup)
        #expect(try OperationType(stringValue: "Recovery") == .recovery)
        #expect(try OperationType(stringValue: "Expiration") == .expiration)
        #expect(try OperationType(stringValue: "Validation") == .validation)
        #expect(try OperationType(stringValue: "KeyRotation") == .keyRotation)
        #expect(try OperationType(stringValue: "GarbageCollection") == .garbageCollection)
    }

    @Test("init(stringValue:) throws for unexpected input")
    func rejectsUnexpectedInput() {
        #expect(throws: OperationTypeError.unexpected("other")) {
            _ = try OperationType(stringValue: "other")
        }
    }
}

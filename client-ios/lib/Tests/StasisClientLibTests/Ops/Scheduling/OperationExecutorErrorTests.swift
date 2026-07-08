import Foundation
@testable import StasisClientLib
import Testing

@Suite("OperationExecutorError")
struct OperationExecutorErrorTests {
    private let operation = UUID(uuidString: "A5C9A40E-352E-4EEE-BFAE-1B44B42C3FE0")!
    private let existing = UUID(uuidString: "38E779A1-A774-4520-9E27-73E26CD35565")!

    @Test("describes notImplemented")
    func notImplemented() {
        #expect(OperationExecutorError.notImplemented("test").errorDescription == "[test] is not implemented")
    }

    @Test("describes operationNotFound")
    func operationNotFound() {
        #expect(
            OperationExecutorError.operationNotFound(operation).errorDescription
                == "Operation [\(operation.uuidString)] not found"
        )
    }

    @Test("describes operationAlreadyActive")
    func operationAlreadyActive() {
        #expect(
            OperationExecutorError.operationAlreadyActive(type: .backup, existing: existing).errorDescription
                == "Cannot start [Backup] operation; [Backup] with ID [\(existing.uuidString)] is already active"
        )
    }

    @Test("describes cannotResumeCompleted")
    func cannotResumeCompleted() {
        #expect(
            OperationExecutorError.cannotResumeCompleted(operation: operation).errorDescription
                == "Cannot resume operation with ID [\(operation.uuidString)]; operation already completed"
        )
    }

    @Test("describes cannotResumeMissing")
    func cannotResumeMissing() {
        #expect(
            OperationExecutorError.cannotResumeMissing(operation: operation).errorDescription
                == "Cannot resume operation with ID [\(operation.uuidString)]; no existing state was found"
        )
    }
}

@testable import StasisClient
import Testing

@MainActor
@Suite("UpdatePasswordSheet")
struct UpdatePasswordSheetTests {
    @Test("rejects an empty current password")
    func rejectsEmptyCurrent() {
        #expect(UpdatePasswordSheet.canSubmit(
            currentPassword: "",
            newPassword: "new",
            newPasswordConfirmation: "new"
        ) == false)
    }

    @Test("rejects an empty new password")
    func rejectsEmptyNew() {
        #expect(UpdatePasswordSheet.canSubmit(
            currentPassword: "old",
            newPassword: "",
            newPasswordConfirmation: ""
        ) == false)
    }

    @Test("rejects mismatched new password confirmation")
    func rejectsMismatchedConfirmation() {
        #expect(UpdatePasswordSheet.canSubmit(
            currentPassword: "old",
            newPassword: "new",
            newPasswordConfirmation: "different"
        ) == false)
    }

    @Test("accepts when current is set and new fields match")
    func acceptsValid() {
        #expect(UpdatePasswordSheet.canSubmit(
            currentPassword: "old",
            newPassword: "new",
            newPasswordConfirmation: "new"
        ) == true)
    }
}

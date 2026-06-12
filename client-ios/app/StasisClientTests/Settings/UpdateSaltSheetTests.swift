@testable import StasisClient
import Testing

@MainActor
@Suite("UpdateSaltSheet")
struct UpdateSaltSheetTests {
    @Test("rejects an empty current password")
    func rejectsEmptyCurrent() {
        #expect(UpdateSaltSheet.canSubmit(
            currentPassword: "",
            newSalt: "abc",
            newSaltConfirmation: "abc"
        ) == false)
    }

    @Test("rejects an empty new salt")
    func rejectsEmptySalt() {
        #expect(UpdateSaltSheet.canSubmit(
            currentPassword: "old",
            newSalt: "",
            newSaltConfirmation: ""
        ) == false)
    }

    @Test("rejects mismatched salt confirmation")
    func rejectsMismatchedConfirmation() {
        #expect(UpdateSaltSheet.canSubmit(
            currentPassword: "old",
            newSalt: "abc",
            newSaltConfirmation: "xyz"
        ) == false)
    }

    @Test("accepts when current is set and salts match")
    func acceptsValid() {
        #expect(UpdateSaltSheet.canSubmit(
            currentPassword: "old",
            newSalt: "abc",
            newSaltConfirmation: "abc"
        ) == true)
    }
}

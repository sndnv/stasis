import StasisClientLib
import SwiftUI

struct UpdatePasswordSheet: View {
    let onUpdate: (_ currentPassword: String, _ newPassword: String) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var currentPassword: String = ""
    @State private var newPassword: String = ""
    @State private var newPasswordConfirmation: String = ""
    @State private var isSubmitting: Bool = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Current Password") {
                    SecureField("Password", text: $currentPassword)
                        .textContentType(.password)
                }
                Section("New Password") {
                    SecureField("New password", text: $newPassword)
                        .textContentType(.newPassword)
                    SecureField("Confirm new password", text: $newPasswordConfirmation)
                        .textContentType(.newPassword)
                }
                if let error {
                    Section {
                        Text(error).foregroundStyle(.red).font(.caption)
                    }
                }
            }
            .navigationTitle("Update Password")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Update") { Task { await runUpdate() } }
                        .disabled(!canSubmit || isSubmitting)
                }
            }
        }
    }

    private var canSubmit: Bool {
        Self.canSubmit(
            currentPassword: currentPassword,
            newPassword: newPassword,
            newPasswordConfirmation: newPasswordConfirmation
        )
    }

    static func canSubmit(
        currentPassword: String,
        newPassword: String,
        newPasswordConfirmation: String
    ) -> Bool {
        !currentPassword.isEmpty
            && !newPassword.isEmpty
            && newPassword == newPasswordConfirmation
    }

    private func runUpdate() async {
        error = nil
        isSubmitting = true
        do {
            try await onUpdate(currentPassword, newPassword)
            dismiss()
        } catch is InvalidUserCredentials {
            error = "Invalid current password."
        } catch {
            self.error = error.localizedDescription
        }
        isSubmitting = false
    }
}

#if DEBUG
#Preview {
    UpdatePasswordSheet(onUpdate: { _, _ in })
}
#endif

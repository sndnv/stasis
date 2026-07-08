import StasisClientLib
import SwiftUI

struct UpdateSaltSheet: View {
    let onUpdate: (_ currentPassword: String, _ newSalt: String) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(ToastCenter.self) private var toasts
    @State private var currentPassword: String = ""
    @State private var newSalt: String = ""
    @State private var newSaltConfirmation: String = ""
    @State private var isSubmitting: Bool = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Current Password") {
                    SecureField("Password", text: $currentPassword)
                        .textContentType(.password)
                }
                Section("New Salt") {
                    TextField("New salt", text: $newSalt)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    TextField("Confirm new salt", text: $newSaltConfirmation)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
                if let error {
                    Section {
                        Text(error).foregroundStyle(.red).font(.caption)
                    }
                }
            }
            .navigationTitle("Update Salt")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(isSubmitting)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Update") { Task { await runUpdate() } }
                        .disabled(!canSubmit || isSubmitting)
                }
            }
            .submittingOverlay(isSubmitting)
        }
    }

    private var canSubmit: Bool {
        Self.canSubmit(
            currentPassword: currentPassword,
            newSalt: newSalt,
            newSaltConfirmation: newSaltConfirmation
        )
    }

    static func canSubmit(
        currentPassword: String,
        newSalt: String,
        newSaltConfirmation: String
    ) -> Bool {
        !currentPassword.isEmpty
            && !newSalt.isEmpty
            && newSalt == newSaltConfirmation
    }

    private func runUpdate() async {
        error = nil
        isSubmitting = true
        do {
            try await onUpdate(currentPassword, newSalt)
            toasts.show("Salt updated")
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
    UpdateSaltSheet(onUpdate: { _, _ in })
        .environment(ToastCenter(displayDuration: .seconds(2.5)))
}
#endif

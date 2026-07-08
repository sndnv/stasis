import SwiftUI

struct LoginReEncryptDeviceSecretView: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.dismiss) private var dismiss

    @State private var newPassword: String = ""
    @State private var newPasswordVerify: String = ""
    @State private var oldPassword: String = ""
    @State private var inProgress: Bool = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    SecureField("New Password", text: $newPassword)
                } header: {
                    Text("New Password")
                } footer: {
                    Text("The password the device secret will be re-encrypted with.")
                }

                Section {
                    SecureField("Verify Password", text: $newPasswordVerify)
                } header: {
                    Text("Verify Password")
                } footer: {
                    if !newPasswordVerify.isEmpty && !newPasswordsMatch {
                        Text("Passwords do not match").foregroundStyle(.red)
                    }
                }

                Section {
                    SecureField("Old Password", text: $oldPassword)
                } header: {
                    Text("Old Password")
                } footer: {
                    Text("The password the device secret is currently encrypted with.")
                }
            }
            .navigationTitle("Re-encrypt device secret")
            .navigationBarTitleDisplayMode(.inline)
            .submittingOverlay(inProgress)
            .alert("Re-encrypt Failed", isPresented: errorBinding) {
                Button("OK") { error = nil }
            } message: {
                Text(error ?? "")
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(inProgress)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Re-encrypt") { run() }
                        .disabled(!canSubmit || inProgress)
                }
            }
        }
    }

    private var errorBinding: Binding<Bool> {
        Binding(get: { error != nil }, set: { if !$0 { error = nil } })
    }

    private var newPasswordsMatch: Bool {
        newPassword == newPasswordVerify
    }

    private var canSubmit: Bool {
        !newPassword.isEmpty && newPasswordsMatch && !oldPassword.isEmpty
    }

    private func run() {
        inProgress = true
        Task {
            do {
                try await container.reEncryptDeviceSecret(
                    currentPassword: newPassword,
                    oldPassword: oldPassword
                )
                inProgress = false
                dismiss()
            } catch {
                inProgress = false
                self.error = error.localizedDescription
            }
        }
    }
}

#Preview {
    LoginReEncryptDeviceSecretView()
        .environment(AppContainer())
}

import SwiftUI

struct PushDeviceSecretSheet: View {
    let onPush: (String, String?) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var password: String = ""
    @State private var useSeparateRemotePassword: Bool = false
    @State private var remotePassword: String = ""
    @State private var isPushing: Bool = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Current Password") {
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                }
                Section {
                    Toggle("Use separate remote password", isOn: $useSeparateRemotePassword)
                    if useSeparateRemotePassword {
                        SecureField("Remote password", text: $remotePassword)
                            .textContentType(.password)
                    }
                } footer: {
                    Text("Leave off to encrypt the remote secret with the current password.")
                }
                if let error {
                    Section {
                        Text(error).foregroundStyle(.red).font(.caption)
                    }
                }
            }
            .navigationTitle("Push Device Secret")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Push") { Task { await runPush() } }
                        .disabled(!canSubmit || isPushing)
                }
            }
        }
    }

    private var canSubmit: Bool {
        !password.isEmpty
            && (!useSeparateRemotePassword || !remotePassword.isEmpty)
    }

    private func runPush() async {
        error = nil
        isPushing = true
        do {
            try await onPush(password, useSeparateRemotePassword ? remotePassword : nil)
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
        isPushing = false
    }
}

#if DEBUG
#Preview {
    PushDeviceSecretSheet(onPush: { _, _ in })
}
#endif

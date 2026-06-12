import SwiftUI

struct PullDeviceSecretSheet: View {
    let onCheckExists: () async throws -> Bool
    let onPull: (String, String?) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var checking: Bool = true
    @State private var exists: Bool = false
    @State private var password: String = ""
    @State private var useSeparateRemotePassword: Bool = false
    @State private var remotePassword: String = ""
    @State private var isPulling: Bool = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                if checking {
                    Section { ProgressView().frame(maxWidth: .infinity) }
                } else if !exists {
                    Section {
                        ContentUnavailableView(
                            "No Remote Secret",
                            systemImage: "icloud.slash",
                            description: Text("No device secret has been pushed to the server.")
                        )
                    }
                } else {
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
                        Text("Leave off if the remote secret was pushed with the current password.")
                    }
                }
                if let error {
                    Section {
                        Text(error).foregroundStyle(.red).font(.caption)
                    }
                }
            }
            .navigationTitle("Pull Device Secret")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Pull") { Task { await runPull() } }
                        .disabled(!canSubmit || isPulling)
                }
            }
            .task { await checkRemote() }
        }
    }

    private var canSubmit: Bool {
        exists
            && !password.isEmpty
            && (!useSeparateRemotePassword || !remotePassword.isEmpty)
    }

    private func checkRemote() async {
        checking = true
        do {
            exists = try await onCheckExists()
        } catch {
            self.error = error.localizedDescription
            exists = false
        }
        checking = false
    }

    private func runPull() async {
        error = nil
        isPulling = true
        do {
            try await onPull(password, useSeparateRemotePassword ? remotePassword : nil)
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
        isPulling = false
    }
}

#if DEBUG
#Preview("checking") {
    PullDeviceSecretSheet(
        onCheckExists: {
            try await Task.sleep(for: .seconds(10))
            return true
        },
        onPull: { _, _ in }
    )
}

#Preview("ready") {
    PullDeviceSecretSheet(onCheckExists: { true }, onPull: { _, _ in })
}

#Preview("missing") {
    PullDeviceSecretSheet(onCheckExists: { false }, onPull: { _, _ in })
}
#endif

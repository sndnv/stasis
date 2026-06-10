import SwiftUI

struct LoginMoreOptionsView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var showReEncrypt: Bool = false
    @State private var showReInitialize: Bool = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Button { showReEncrypt = true } label: {
                        optionLabel(
                            title: "Re-encrypt device secret",
                            detail: "If your password was changed on a different device, this allows decrypting "
                                + "the secret of this device using the old password and re-encrypting it using the new one."
                        )
                    }
                    .buttonStyle(.plain)
                }

                Section {
                    Button { showReInitialize = true } label: {
                        optionLabel(
                            title: "Re-initialize device",
                            detail: "Allows re-running the bootstrap process for this device."
                        )
                    }
                    .buttonStyle(.plain)
                }

                Section {
                    optionLabel(
                        title: "Reset user password",
                        detail: "You can reset your password after logging in or, if that's not possible, "
                            + "contact your system administrator.",
                        disabled: true
                    )
                }
            }
            .navigationTitle("More options")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Close") { dismiss() }
                }
            }
        }
        .sheet(isPresented: $showReEncrypt) {
            LoginReEncryptDeviceSecretView()
        }
        .sheet(isPresented: $showReInitialize) {
            LoginReInitializeDeviceView()
        }
    }

    @ViewBuilder
    private func optionLabel(title: String, detail: String, disabled: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.body)
                .foregroundStyle(disabled ? .secondary : .primary)
            Text(detail)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
    }
}

#Preview {
    LoginMoreOptionsView()
}

import StasisClientLib
import SwiftUI

struct ImportDeviceSecretSheet: View {
    let onImport: (Data, String) async throws -> Void

    @Environment(\.dismiss) private var dismiss
    @Environment(ToastCenter.self) private var toasts
    @State private var secret: String = ""
    @State private var password: String = ""
    @State private var isImporting: Bool = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextField("Base64 secret", text: $secret, axis: .vertical)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .lineLimit(3...8)
                        .font(.caption.monospaced())
                } header: {
                    Text("Secret")
                } footer: {
                    Text("Paste the Base64-encoded device secret you exported earlier.")
                }
                Section("Current Password") {
                    SecureField("Password", text: $password)
                        .textContentType(.password)
                }
                if let error {
                    Section {
                        Text(error).foregroundStyle(.red).font(.caption)
                    }
                }
            }
            .navigationTitle("Import Device Secret")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(isImporting)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Import") { Task { await runImport() } }
                        .disabled(!canSubmit || isImporting)
                }
            }
            .submittingOverlay(isImporting)
        }
    }

    private var canSubmit: Bool {
        !secret.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !password.isEmpty
    }

    private func runImport() async {
        let trimmed = secret.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let decoded = Data(base64UrlEncoded: trimmed)
            ?? Data(base64Encoded: trimmed, options: .ignoreUnknownCharacters)
        else {
            error = "Secret is not valid Base64."
            return
        }
        error = nil
        isImporting = true
        do {
            try await onImport(decoded, password)
            toasts.show("Device secret imported")
            dismiss()
        } catch {
            self.error = error.localizedDescription
        }
        isImporting = false
    }
}

#if DEBUG
#Preview {
    ImportDeviceSecretSheet(onImport: { _, _ in })
        .environment(ToastCenter(displayDuration: .seconds(2.5)))
}
#endif

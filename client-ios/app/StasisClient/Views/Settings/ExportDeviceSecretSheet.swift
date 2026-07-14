import SwiftUI
import UIKit
import UniformTypeIdentifiers

struct ExportDeviceSecretSheet: View {
    let secret: String?

    @Environment(\.dismiss) private var dismiss
    @State private var copied: Bool = false

    var body: some View {
        NavigationStack {
            Form {
                if let secret, !secret.isEmpty {
                    Section {
                        Text(secret)
                            .font(.caption.monospaced())
                            .textSelection(.enabled)
                    } header: {
                        Text("Device Secret")
                    } footer: {
                        Text("Keep this value private. Anyone with it can decrypt your backups.")
                    }
                    Section {
                        Button {
                            UIPasteboard.general.setItems(
                                [[UTType.utf8PlainText.identifier: secret]],
                                options: [
                                    .localOnly: true,
                                    .expirationDate: Date().addingTimeInterval(120)
                                ]
                            )
                            copied = true
                        } label: {
                            Label(copied ? "Copied" : "Copy to Clipboard", systemImage: "doc.on.doc")
                        }
                        .disabled(copied)
                    } footer: {
                        Text("Copying places the secret on the clipboard, where other apps can read it. It stays on this device only and is cleared automatically after a short time.")
                    }
                } else {
                    Section {
                        ContentUnavailableView(
                            "No Secret Available",
                            systemImage: "key.slash",
                            description: Text("The device secret is not available locally.")
                        )
                    }
                }
            }
            .navigationTitle("Export Device Secret")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

#if DEBUG
#Preview("with secret") {
    ExportDeviceSecretSheet(secret: "VGhpcyBpcyBhIHRlc3Qgc2VjcmV0IHZhbHVlIGZvciBwcmV2aWV3IHB1cnBvc2VzLg==")
}

#Preview("missing") {
    ExportDeviceSecretSheet(secret: nil)
}
#endif

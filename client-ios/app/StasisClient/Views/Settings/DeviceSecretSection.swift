import StasisClientLib
import SwiftUI

struct DeviceSecretSection: View {
    @Environment(AppContainer.self) private var container
    @State private var showExportConfirmation: Bool = false
    @State private var showImportConfirmation: Bool = false
    @State private var showExport: Bool = false
    @State private var showImport: Bool = false
    @State private var showPush: Bool = false
    @State private var showPull: Bool = false

    var body: some View {
        Section("Device Secret") {
            Button {
                showPush = true
            } label: {
                Label("Push Secret", systemImage: "icloud.and.arrow.up")
            }
            Button {
                showPull = true
            } label: {
                Label("Pull Secret", systemImage: "icloud.and.arrow.down")
            }
            Button {
                showExportConfirmation = true
            } label: {
                Label("Export Locally", systemImage: "square.and.arrow.up")
            }
            Button {
                showImportConfirmation = true
            } label: {
                Label("Import Locally", systemImage: "square.and.arrow.down")
            }
        }
        .confirmationDialog(
            "Show device secret?",
            isPresented: $showExportConfirmation,
            titleVisibility: .visible
        ) {
            Button("Show", role: .destructive) { showExport = true }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("The device secret will be displayed in plain text. Anyone with this value can decrypt your backups.")
        }
        .confirmationDialog(
            "Replace device secret?",
            isPresented: $showImportConfirmation,
            titleVisibility: .visible
        ) {
            Button("Continue", role: .destructive) { showImport = true }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("Replacing the device secret can make existing encrypted backups unrecoverable.")
        }
        .sheet(isPresented: $showExport) {
            ExportDeviceSecretSheet(secret: secretBase64)
        }
        .sheet(isPresented: $showImport) {
            ImportDeviceSecretSheet(onImport: { plaintext, password in
                try await container.importDeviceSecret(plaintext: plaintext, password: password)
            })
        }
        .sheet(isPresented: $showPush) {
            PushDeviceSecretSheet(onPush: { password, remote in
                try await container.pushDeviceSecret(password: password, remotePassword: remote)
            })
        }
        .sheet(isPresented: $showPull) {
            PullDeviceSecretSheet(
                onCheckExists: { try await container.remoteDeviceSecretExists() },
                onPull: { password, remote in
                    try await container.pullDeviceSecret(password: password, remotePassword: remote)
                }
            )
        }
    }

    private var secretBase64: String? {
        container.sessionTokenStore.loadPlaintextDeviceSecret()?.base64UrlEncodedString()
    }
}

#Preview {
    Form { DeviceSecretSection() }
        .environment(AppContainer())
}

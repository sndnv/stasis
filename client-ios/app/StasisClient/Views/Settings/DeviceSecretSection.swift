import StasisClientLib
import SwiftUI

struct DeviceSecretSection: View {
    @Environment(AppContainer.self) private var container
    @State private var showExportConfirmation: Bool = false
    @State private var showImportConfirmation: Bool = false
    @State private var activeSheet: ActiveSheet?

    private enum ActiveSheet: Identifiable {
        case export
        case importSecret
        case push
        case pull

        var id: Self { self }
    }

    var body: some View {
        Section("Device Secret") {
            Button {
                activeSheet = .push
            } label: {
                Label("Push Secret", systemImage: "icloud.and.arrow.up")
            }
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .export:
                    ExportDeviceSecretSheet(secret: secretBase64)
                case .importSecret:
                    ImportDeviceSecretSheet(onImport: { plaintext, password in
                        try await container.importDeviceSecret(plaintext: plaintext, password: password)
                    })
                case .push:
                    PushDeviceSecretSheet(onPush: { password, remote in
                        try await container.pushDeviceSecret(password: password, remotePassword: remote)
                    })
                case .pull:
                    PullDeviceSecretSheet(
                        onCheckExists: { try await container.remoteDeviceSecretExists() },
                        onPull: { password, remote in
                            try await container.pullDeviceSecret(password: password, remotePassword: remote)
                        }
                    )
                }
            }
            Button {
                activeSheet = .pull
            } label: {
                Label("Pull Secret", systemImage: "icloud.and.arrow.down")
            }
            Button {
                showExportConfirmation = true
            } label: {
                Label("Export Locally", systemImage: "square.and.arrow.up")
            }
            .confirmationDialog(
                "Show device secret?",
                isPresented: $showExportConfirmation,
                titleVisibility: .visible
            ) {
                Button("Show", role: .destructive) { activeSheet = .export }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("The device secret will be displayed in plain text. Anyone with this value can decrypt your backups.")
            }
            Button {
                showImportConfirmation = true
            } label: {
                Label("Import Locally", systemImage: "square.and.arrow.down")
            }
            .confirmationDialog(
                "Replace device secret?",
                isPresented: $showImportConfirmation,
                titleVisibility: .visible
            ) {
                Button("Continue", role: .destructive) { activeSheet = .importSecret }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Replacing the device secret can make existing encrypted backups unrecoverable.")
            }
        }
    }

    private var secretBase64: String? {
        container.sessionTokenStore.loadPlaintextDeviceSecret()?.base64UrlEncodedString()
    }
}

#Preview {
    Form { DeviceSecretSection() }
        .environment(AppContainer())
        .environment(ToastCenter(displayDuration: .seconds(2.5)))
}

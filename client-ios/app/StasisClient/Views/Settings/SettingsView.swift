import SwiftUI

struct SettingsView: View {
    @Environment(AppContainer.self) private var container
    @State private var showResetConfirmation: Bool = false
    @State private var resetError: String?

    var body: some View {
        Form {
            DateTimeFormatSection()
            UserCredentialsSection()
            DeviceSecretSection()
            AnalyticsSection()
            CommandsSection()
            AdvancedSection()
            DebugSection { showResetConfirmation = true }
        }
        .navigationTitle("Settings")
        .defaultAppStorage(container.settings)
        .confirmationDialog(
            "Reset configuration?",
            isPresented: $showResetConfirmation,
            titleVisibility: .visible
        ) {
            Button("Reset", role: .destructive) {
                Task { await runReset() }
            }
            Button("Cancel", role: .cancel) {}
        } message: {
            Text("This logs you out, clears server configuration, rules, and schedules. You'll need to bootstrap the device again.")
        }
        .alert("Reset Failed", isPresented: resetErrorBinding) {
            Button("OK") { resetError = nil }
        } message: {
            Text(resetError ?? "")
        }
    }

    private var resetErrorBinding: Binding<Bool> {
        Binding(get: { resetError != nil }, set: { if !$0 { resetError = nil } })
    }

    private func runReset() async {
        do {
            try await container.resetConfiguration()
        } catch {
            resetError = error.localizedDescription
        }
    }
}

#Preview {
    NavigationStack {
        SettingsView()
    }
    .environment(AppContainer())
    .environment(ToastCenter(displayDuration: .seconds(2.5)))
}

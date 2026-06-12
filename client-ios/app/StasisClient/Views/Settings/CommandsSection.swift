import SwiftUI

struct CommandsSection: View {
    @AppStorage(Settings.Keys.commandRefreshInterval)
    private var commandRefreshInterval: TimeInterval = Settings.Defaults.commandRefreshInterval
    @State private var showAvailable: Bool = false
    @State private var showSupported: Bool = false

    var body: some View {
        Section {
            Button {
                showAvailable = true
            } label: {
                Label("Show Available", systemImage: "list.bullet.rectangle")
            }
            Button {
                showSupported = true
            } label: {
                Label("Show Supported", systemImage: "checklist")
            }
            IntervalPicker(
                title: "Refresh Interval",
                seconds: $commandRefreshInterval,
                options: SettingsIntervalOptions.allShort
            )
        } header: {
            Text("Commands")
        } footer: {
            Text("Changes to the refresh interval apply after restart.")
        }
        .sheet(isPresented: $showAvailable) { AvailableCommandsSheet() }
        .sheet(isPresented: $showSupported) { SupportedCommandsSheet() }
    }
}

#Preview {
    Form { CommandsSection() }
}

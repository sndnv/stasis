import SwiftUI

struct CommandsSection: View {
    @AppStorage(Settings.Keys.commandRefreshInterval)
    private var commandRefreshInterval: TimeInterval = Settings.Defaults.commandRefreshInterval
    @State private var activeSheet: ActiveSheet?

    private enum ActiveSheet: Identifiable {
        case available
        case supported

        var id: Self { self }
    }

    var body: some View {
        Section {
            Button {
                activeSheet = .available
            } label: {
                Label("Show Available", systemImage: "list.bullet.rectangle")
            }
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .available:
                    AvailableCommandsSheet()
                case .supported:
                    SupportedCommandsSheet()
                }
            }
            Button {
                activeSheet = .supported
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
    }
}

#Preview {
    Form { CommandsSection() }
}

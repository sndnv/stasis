import SwiftUI

struct CommandsSection: View {
    @AppStorage(Settings.Keys.commandRefreshInterval)
    private var commandRefreshInterval: TimeInterval = Settings.Defaults.commandRefreshInterval

    var body: some View {
        Section {
            PlaceholderActionRow(title: "Show Available")
            PlaceholderActionRow(title: "Show Supported")
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

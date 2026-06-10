import SwiftUI

struct DebugSection: View {
    @AppStorage(Settings.Keys.pingInterval)
    private var pingInterval: TimeInterval = Settings.Defaults.pingInterval
    @AppStorage(Settings.Keys.discoveryInterval)
    private var discoveryInterval: TimeInterval = Settings.Defaults.discoveryInterval
    @AppStorage(Settings.Keys.cachePendingInterval)
    private var cachePendingInterval: TimeInterval = Settings.Defaults.cachePendingInterval
    @AppStorage(Settings.Keys.cacheActiveInterval)
    private var cacheActiveInterval: TimeInterval = Settings.Defaults.cacheActiveInterval

    let onResetTapped: () -> Void

    var body: some View {
        Section {
            IntervalPicker(
                title: "Ping Interval", seconds: $pingInterval,
                options: SettingsIntervalOptions.allShort
            )
            IntervalPicker(
                title: "Discovery Interval", seconds: $discoveryInterval,
                options: SettingsIntervalOptions.allLong
            )
            IntervalPicker(
                title: "Cache Pending Interval", seconds: $cachePendingInterval,
                options: SettingsIntervalOptions.allLong
            )
            IntervalPicker(
                title: "Cache Active Interval", seconds: $cacheActiveInterval,
                options: SettingsIntervalOptions.allShort
            )
            PlaceholderActionRow(title: "Show Cache Statistics")
            Button(role: .destructive, action: onResetTapped) {
                Label("Reset Configuration", systemImage: "arrow.counterclockwise")
            }
        } header: {
            Text("Debug")
        } footer: {
            Text("Changes to intervals apply after restart.")
        }
    }
}

#Preview {
    Form { DebugSection(onResetTapped: {}) }
}

import SwiftUI

struct AnalyticsSection: View {
    @AppStorage(Settings.Keys.analyticsEnabled)
    private var enabled: Bool = Settings.Defaults.analyticsEnabled
    @AppStorage(Settings.Keys.analyticsKeepEvents)
    private var keepEvents: Bool = Settings.Defaults.analyticsKeepEvents
    @AppStorage(Settings.Keys.analyticsKeepFailures)
    private var keepFailures: Bool = Settings.Defaults.analyticsKeepFailures
    @AppStorage(Settings.Keys.analyticsPersistenceInterval)
    private var persistenceInterval: TimeInterval = Settings.Defaults.analyticsPersistenceInterval
    @AppStorage(Settings.Keys.analyticsTransmissionInterval)
    private var transmissionInterval: TimeInterval = Settings.Defaults.analyticsTransmissionInterval

    var body: some View {
        Section {
            Toggle("Enabled", isOn: $enabled)
            Toggle("Keep Events", isOn: $keepEvents).disabled(!enabled)
            Toggle("Keep Failures", isOn: $keepFailures).disabled(!enabled)
            IntervalPicker(
                title: "Persistence Interval",
                seconds: $persistenceInterval,
                options: SettingsIntervalOptions.allShort
            ).disabled(!enabled)
            IntervalPicker(
                title: "Transmission Interval",
                seconds: $transmissionInterval,
                options: SettingsIntervalOptions.allLong
            ).disabled(!enabled)
            PlaceholderActionRow(title: "Show Collected").disabled(!enabled)
        } header: {
            Text("Analytics")
        } footer: {
            Text("Changes to intervals apply after restart.")
        }
    }
}

#Preview {
    Form { AnalyticsSection() }
}

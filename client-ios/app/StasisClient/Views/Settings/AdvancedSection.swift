import SwiftUI

struct AdvancedSection: View {
    @AppStorage(Settings.Keys.restrictionsIgnored)
    private var restrictionsIgnored: Bool = Settings.Defaults.restrictionsIgnored
    @AppStorage(Settings.Keys.schedulingEnabled)
    private var schedulingEnabled: Bool = Settings.Defaults.schedulingEnabled

    var body: some View {
        Section {
            Toggle("Restrictions Ignored", isOn: $restrictionsIgnored)
            Toggle("Scheduling Enabled", isOn: $schedulingEnabled)
            PlaceholderActionRow(title: "Show Permissions")
        } header: {
            Text("Advanced")
        } footer: {
            Text("Changes apply after restart.")
        }
    }
}

#Preview {
    Form { AdvancedSection() }
}

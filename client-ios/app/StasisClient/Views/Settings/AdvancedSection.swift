import SwiftUI

struct AdvancedSection: View {
    @AppStorage(Settings.Keys.restrictionsIgnored)
    private var restrictionsIgnored: Bool = Settings.Defaults.restrictionsIgnored
    @AppStorage(Settings.Keys.schedulingEnabled)
    private var schedulingEnabled: Bool = Settings.Defaults.schedulingEnabled

    @State private var showPermissions: Bool = false

    var body: some View {
        Section {
            Toggle("Restrictions Ignored", isOn: $restrictionsIgnored)
            Toggle("Scheduling Enabled", isOn: $schedulingEnabled)
            Button {
                showPermissions = true
            } label: {
                Label("Show Permissions", systemImage: "lock.shield")
            }
        } header: {
            Text("Advanced")
        } footer: {
            Text("Changes apply after restart.")
        }
        .sheet(isPresented: $showPermissions) {
            PermissionsSheet()
        }
    }
}

#Preview {
    Form { AdvancedSection() }
}

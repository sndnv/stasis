import SwiftUI

struct AdvancedSection: View {
    @AppStorage(Settings.Keys.schedulingEnabled)
    private var schedulingEnabled: Bool = Settings.Defaults.schedulingEnabled

    @State private var showPermissions: Bool = false

    var body: some View {
        Section {
            Toggle("Scheduling Enabled", isOn: $schedulingEnabled)
            Button {
                showPermissions = true
            } label: {
                Label("Show Permissions", systemImage: "lock.shield")
            }
            .sheet(isPresented: $showPermissions) {
                PermissionsSheet()
            }
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

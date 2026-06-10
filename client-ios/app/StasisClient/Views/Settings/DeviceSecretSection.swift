import SwiftUI

struct DeviceSecretSection: View {
    var body: some View {
        Section("Device Secret") {
            PlaceholderActionRow(title: "Push Secret")
            PlaceholderActionRow(title: "Pull Secret")
            PlaceholderActionRow(title: "Export Locally")
            PlaceholderActionRow(title: "Import Locally")
        }
    }
}

#Preview {
    Form { DeviceSecretSection() }
}

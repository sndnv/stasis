import SwiftUI

struct MoreView: View {
    @Environment(AppContainer.self) private var container

    var body: some View {
        NavigationStack {
            List {
                Section("Manage") {
                    link(.operations, "Operations", "text.justifyleft")
                    link(.status, "Status", "info.circle")
                    link(.rules, "Rules", "text.badge.checkmark")
                    link(.schedules, "Schedules", "clock")
                }
                Section("More") {
                    link(.settings, "Settings", "gearshape")
                    link(.about, "About", "questionmark.circle")
                    Button {
                        Task { await container.logout() }
                    } label: {
                        Label("Logout", systemImage: "rectangle.portrait.and.arrow.right")
                            .foregroundStyle(.primary)
                    }
                }
            }
            .navigationTitle("More")
            .navigationDestination(for: Destination.self) { destination in
                view(for: destination)
            }
        }
    }

    private func link(_ destination: Destination, _ title: String, _ systemImage: String) -> some View {
        NavigationLink(value: destination) {
            Label(title, systemImage: systemImage)
        }
    }

    @ViewBuilder
    private func view(for destination: Destination) -> some View {
        switch destination {
        case .operations: OperationsView()
        case .status: StatusView()
        case .rules: RulesView()
        case .schedules: SchedulesView()
        case .settings: SettingsView()
        case .about: AboutView()
        }
    }

    enum Destination: Hashable {
        case operations, status, rules, schedules, settings, about
    }
}

#Preview {
    MoreView()
        .environment(AppContainer())
}

import SwiftUI

struct MainView: View {
    @Environment(AppContainer.self) private var container
    @State private var selection: TabId = .home
    @State private var lastContentSelection: TabId = .home

    enum TabId: Hashable {
        case home, backup, recover, search
        case operations, rules, schedules
        case status, settings, about, logout
    }

    var body: some View {
        TabView(selection: $selection) {
            TabSection("Backup") {
                Tab("Home", systemImage: "house", value: TabId.home) {
                    HomeView()
                }
                Tab("Backup", systemImage: "square.and.arrow.up", value: TabId.backup) {
                    BackupView()
                }
                Tab("Recover", systemImage: "square.and.arrow.down", value: TabId.recover) {
                    RecoverView()
                }
                Tab("Search", systemImage: "magnifyingglass", value: TabId.search) {
                    SearchView()
                }
            }
            TabSection("Manage") {
                Tab("Operations", systemImage: "text.justifyleft", value: TabId.operations) {
                    OperationsView()
                }
                Tab("Status", systemImage: "info.circle", value: TabId.status) {
                    StatusView()
                }
                Tab("Rules", systemImage: "text.badge.checkmark", value: TabId.rules) {
                    RulesView()
                }
                Tab("Schedules", systemImage: "clock", value: TabId.schedules) {
                    SchedulesView()
                }
            }
            TabSection("More") {
                Tab("Settings", systemImage: "gearshape", value: TabId.settings) {
                    SettingsView()
                }
                Tab("About", systemImage: "questionmark.circle", value: TabId.about) {
                    AboutView()
                }
                Tab("Logout", systemImage: "rectangle.portrait.and.arrow.right", value: TabId.logout) {
                    EmptyView()
                }
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .onChange(of: selection) { _, new in
            if new == .logout {
                selection = lastContentSelection
                Task { await container.logout() }
            } else {
                lastContentSelection = new
            }
        }
    }
}

#Preview {
    MainView()
        .environment(AppContainer())
}

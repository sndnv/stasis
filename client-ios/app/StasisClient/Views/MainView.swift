import SwiftUI

struct MainView: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.horizontalSizeClass) private var horizontalSizeClass
    @State private var selection: TabId = .home

    enum TabId: Hashable {
        case home, backup, recover, search
        case operations, rules, schedules
        case status, settings, about, logout
        case more
    }

    private static let compactPrimary: Set<TabId> = [.home, .backup, .recover, .search]

    var body: some View {
        TabView(selection: $selection) {
            if horizontalSizeClass == .compact {
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
                Tab("More", systemImage: "ellipsis", value: TabId.more) {
                    MoreView()
                }
            } else {
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
                        NavigationStack { OperationsView() }
                    }
                    Tab("Status", systemImage: "info.circle", value: TabId.status) {
                        NavigationStack { StatusView() }
                    }
                    Tab("Rules", systemImage: "text.badge.checkmark", value: TabId.rules) {
                        NavigationStack { RulesView() }
                    }
                    Tab("Schedules", systemImage: "clock", value: TabId.schedules) {
                        NavigationStack { SchedulesView() }
                    }
                }
                TabSection("More") {
                    Tab("Settings", systemImage: "gearshape", value: TabId.settings) {
                        NavigationStack { SettingsView() }
                    }
                    Tab("About", systemImage: "questionmark.circle", value: TabId.about) {
                        NavigationStack { AboutView() }
                    }
                    Tab("Logout", systemImage: "rectangle.portrait.and.arrow.right", value: TabId.logout) {
                        LoggingOutView()
                    }
                }
            }
        }
        .tabViewStyle(.sidebarAdaptable)
        .onChange(of: selection) { _, new in
            if new == .logout {
                Task { await container.logout() }
            }
        }
        .onChange(of: horizontalSizeClass) { _, _ in
            reconcileSelection()
        }
    }

    private func reconcileSelection() {
        if horizontalSizeClass == .compact {
            if !Self.compactPrimary.contains(selection) {
                selection = .more
            }
        } else if selection == .more {
            selection = .home
        }
    }
}

private struct LoggingOutView: View {
    var body: some View {
        VStack(spacing: 16) {
            ProgressView()
                .controlSize(.regular)
            Text("Logging out")
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }
}

#Preview {
    MainView()
        .environment(AppContainer())
}

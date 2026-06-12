import SwiftUI
import UIKit

struct PermissionsSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var model: PermissionsModel = .init()

    var body: some View {
        NavigationStack {
            PermissionsContent(
                state: PermissionsViewState(items: model.items, isLoading: model.isLoading),
                onRefresh: { await model.refresh() },
                onOpenSettings: { openSystemSettings() }
            )
            .navigationTitle("Permissions")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task { await model.refresh() }
        }
    }

    private func openSystemSettings() {
        guard let url = URL(string: UIApplication.openSettingsURLString) else { return }
        UIApplication.shared.open(url)
    }
}

struct PermissionsViewState: Equatable {
    var items: [PermissionsModel.Item] = []
    var isLoading: Bool = false
}

private struct PermissionsContent: View {
    let state: PermissionsViewState
    let onRefresh: () async -> Void
    let onOpenSettings: () -> Void

    var body: some View {
        List {
            if state.isLoading && state.items.isEmpty {
                Section { ProgressView().frame(maxWidth: .infinity) }
            } else {
                Section("Permissions") {
                    ForEach(state.items) { item in
                        PermissionRow(item: item)
                    }
                }
                Section {
                    Button { onOpenSettings() } label: {
                        Label("Open System Settings", systemImage: "gearshape")
                    }
                }
            }
        }
        .refreshable { await onRefresh() }
    }
}

struct PermissionRow: View {
    let item: PermissionsModel.Item

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(item.name).font(.headline)
                Spacer()
                badge
            }
            Text(item.description)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }

    private var badge: some View {
        Text(item.status.label.uppercased())
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(color.opacity(0.15))
            .foregroundStyle(color)
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }

    private var color: Color {
        switch item.status {
        case .granted: .green
        case .denied: .red
        case .provisional: .orange
        case .unknown: .gray
        }
    }
}

#if DEBUG
private struct PreviewHarness: View {
    let state: PermissionsViewState

    var body: some View {
        NavigationStack {
            PermissionsContent(state: state, onRefresh: {}, onOpenSettings: {})
                .navigationTitle("Permissions")
                .navigationBarTitleDisplayMode(.inline)
        }
    }
}

#Preview("loading") {
    PreviewHarness(state: PermissionsViewState(items: [], isLoading: true))
}

#Preview("populated") {
    PreviewHarness(state: PermissionsViewState(
        items: [
            PermissionsModel.Item(
                id: "notifications",
                name: "Notifications",
                description: "Used to notify you of completed and failed backup operations.",
                status: .granted
            ),
            PermissionsModel.Item(
                id: "backgroundRefresh",
                name: "Background App Refresh",
                description: "Allows scheduled backups to run while the app is in the background.",
                status: .denied
            )
        ],
        isLoading: false
    ))
}
#endif

import StasisClientLib
import SwiftUI
import UIKit

struct RulesView: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.openURL) private var openURL
    @State private var model: RulesModel?
    @State private var disableTarget: LibrarySource?
    @State private var resetConfirming = false

    var body: some View {
        NavigationStack {
            RulesViewContent(
                rows: model?.rows ?? [],
                isLoading: model?.isLoading ?? true,
                onEnable: { scheme in Task { await model?.setEnabled(scheme, true) } },
                onDisableRequest: { disableTarget = $0 },
                onReset: { resetConfirming = true }
            )
            .navigationTitle("Rules")
            .task { await startIfNeeded() }
            .refreshable { await model?.refresh() }
            .confirmationDialog(
                "Stop backing up \(disableTarget?.displayName ?? "")?",
                isPresented: disableBinding,
                presenting: disableTarget
            ) { source in
                Button("Stop Backing Up", role: .destructive) {
                    Task { await model?.setEnabled(source.scheme, false) }
                }
                Button("Cancel", role: .cancel) {}
            } message: { source in
                Text("Removes the rule that backs up your \(source.displayName.lowercased()).")
            }
            .confirmationDialog(
                "Reset to defaults?",
                isPresented: $resetConfirming,
                titleVisibility: .visible
            ) {
                Button("Reset", role: .destructive) { Task { await model?.resetToDefaults() } }
                Button("Cancel", role: .cancel) {}
            } message: {
                Text("Removes all source rules.")
            }
            .alert("Permission Required", isPresented: permissionBinding, presenting: model?.permissionDenied) { _ in
                Button("Open Settings") {
                    if let url = URL(string: UIApplication.openSettingsURLString) { openURL(url) }
                    model?.permissionDenied = nil
                }
                Button("Cancel", role: .cancel) { model?.permissionDenied = nil }
            } message: { source in
                Text("Grant \(source.displayName) access in Settings to back it up.")
            }
            .alert("Error", isPresented: errorBinding) {
                Button("OK") { model?.clearError() }
            } message: {
                Text(model?.error ?? "")
            }
        }
    }

    private func startIfNeeded() async {
        if model == nil {
            model = RulesModel(ruleRepository: container.ruleRepository, sources: LibrarySource.all)
        }
        await model?.start()
    }

    private var disableBinding: Binding<Bool> {
        Binding(get: { disableTarget != nil }, set: { if !$0 { disableTarget = nil } })
    }

    private var permissionBinding: Binding<Bool> {
        Binding(
            get: { model?.permissionDenied != nil },
            set: { if !$0 { model?.permissionDenied = nil } }
        )
    }

    private var errorBinding: Binding<Bool> {
        Binding(get: { model?.error != nil }, set: { if !$0 { model?.clearError() } })
    }
}

private struct RulesViewContent: View {
    let rows: [RulesModel.Row]
    let isLoading: Bool
    let onEnable: (String) -> Void
    let onDisableRequest: (LibrarySource) -> Void
    let onReset: () -> Void

    var body: some View {
        List {
            Section {
                if isLoading && rows.isEmpty {
                    ProgressView().frame(maxWidth: .infinity)
                } else {
                    ForEach(rows) { row in
                        LibrarySourceRow(row: row, onEnable: onEnable, onDisableRequest: onDisableRequest)
                    }
                }
            } header: {
                Text("Sources")
            } footer: {
                Text("Choose which libraries stasis backs up. Experimental sources may capture data that cannot be fully restored.")
            }

            Section {
                Button("Reset to Defaults", role: .destructive, action: onReset)
            }
        }
    }
}

private struct LibrarySourceRow: View {
    let row: RulesModel.Row
    let onEnable: (String) -> Void
    let onDisableRequest: (LibrarySource) -> Void

    var body: some View {
        Toggle(isOn: toggleBinding) {
            Label {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text(row.source.displayName)
                        if row.source.isExperimental {
                            Text("Experimental")
                                .font(.caption2.weight(.semibold))
                                .padding(.horizontal, 6)
                                .padding(.vertical, 2)
                                .background(Color.orange.opacity(0.2), in: Capsule())
                                .foregroundStyle(Color.orange)
                        }
                    }
                    if let footer {
                        Text(footer.text).font(.caption).foregroundStyle(footer.warning ? Color.red : Color.secondary)
                    }
                }
            } icon: {
                Image(systemName: row.source.systemImage)
            }
        }
    }

    private var toggleBinding: Binding<Bool> {
        Binding(
            get: { row.isEnabled },
            set: { enabled in
                if enabled {
                    onEnable(row.source.scheme)
                } else {
                    onDisableRequest(row.source)
                }
            }
        )
    }

    private var footer: (text: String, warning: Bool)? {
        if row.permission == .denied {
            return ("Access not granted", true)
        }
        if row.isEnabled && row.totalSets > 1 {
            return ("Backing up \(row.enabledCount) of \(row.totalSets) sets", !row.inDefault)
        }
        return nil
    }
}

#if DEBUG
private struct RulesPreviewHarness: View {
    let rows: [RulesModel.Row]
    let isLoading: Bool

    var body: some View {
        NavigationStack {
            RulesViewContent(
                rows: rows,
                isLoading: isLoading,
                onEnable: { _ in },
                onDisableRequest: { _ in },
                onReset: {}
            )
            .navigationTitle("Rules")
        }
    }
}

private extension RulesModel.Row {
    static func photos(enabled: Bool, permission: LibraryPermissionStatus = .granted) -> RulesModel.Row {
        RulesModel.Row(
            source: .photos,
            isEnabled: enabled,
            permission: permission,
            enabledCount: enabled ? 1 : 0,
            totalSets: 1,
            inDefault: enabled
        )
    }
}

#Preview("loading") {
    RulesPreviewHarness(rows: [], isLoading: true)
}

#Preview("photos off") {
    RulesPreviewHarness(rows: [.photos(enabled: false)], isLoading: false)
}

#Preview("photos on") {
    RulesPreviewHarness(rows: [.photos(enabled: true)], isLoading: false)
}

#Preview("access denied") {
    RulesPreviewHarness(rows: [.photos(enabled: false, permission: .denied)], isLoading: false)
}
#endif

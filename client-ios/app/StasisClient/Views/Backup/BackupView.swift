import StasisClientLib
import SwiftUI

struct BackupView: View {
    @Environment(AppContainer.self) private var container
    @State private var model: BackupModel?
    @State private var formMode: DefinitionFormSheet.Mode?
    @State private var deletionTarget: DatasetDefinition?

    var body: some View {
        NavigationStack {
            BackupViewContent(
                state: state,
                onRefresh: { await model?.refresh() },
                onClearError: { model?.clearError() },
                onAdd: { presentCreateForm() },
                onEdit: { definition in formMode = .edit(definition) },
                onDelete: { definition in deletionTarget = definition }
            )
            .navigationDestination(for: DatasetDefinition.self) { definition in
                DefinitionDetailView(definition: definition)
            }
            .task { await loadIfNeeded() }
            .sheet(item: $formMode) { mode in
                DefinitionFormSheet(
                    mode: mode,
                    onCreate: { request in await model?.createDefinition(request) ?? false },
                    onUpdate: { id, request in await model?.updateDefinition(id, with: request) ?? false }
                )
            }
            .confirmationDialog(
                "Delete definition?",
                isPresented: deletionBinding,
                presenting: deletionTarget
            ) { definition in
                Button("Delete", role: .destructive) {
                    Task { await model?.deleteDefinition(definition.id) }
                }
                Button("Cancel", role: .cancel) {}
            } message: { definition in
                Text("Removes \(definition.info) from the server. Entries are unaffected.")
            }
        }
    }

    private var state: BackupViewState {
        guard let model else { return .initial }
        return BackupViewState(
            definitions: model.definitions,
            defaultDefinitionId: model.defaultDefinitionId,
            isLoading: model.isLoading,
            error: model.error
        )
    }

    private var deletionBinding: Binding<Bool> {
        Binding(
            get: { deletionTarget != nil },
            set: { if !$0 { deletionTarget = nil } }
        )
    }

    private func presentCreateForm() {
        guard let model else { return }
        formMode = .create(device: model.selfDevice)
    }

    private func loadIfNeeded() async {
        if model == nil {
            guard let session = container.session else { return }
            model = BackupModel(session: session)
        }
        await model?.load()
    }
}

struct BackupViewState: Equatable {
    var definitions: [DatasetDefinition] = []
    var defaultDefinitionId: DatasetDefinitionId?
    var isLoading: Bool = false
    var error: String?

    static let initial = BackupViewState(isLoading: true)
}

extension DefinitionFormSheet.Mode: Identifiable {
    public var id: String {
        switch self {
        case .create: "create"
        case .edit(let definition): "edit-\(definition.id.uuidString)"
        }
    }
}

private struct BackupViewContent: View {
    let state: BackupViewState
    let onRefresh: () async -> Void
    let onClearError: () -> Void
    let onAdd: () -> Void
    let onEdit: (DatasetDefinition) -> Void
    let onDelete: (DatasetDefinition) -> Void

    var body: some View {
        List {
            if state.isLoading && state.definitions.isEmpty {
                Section { ProgressView().frame(maxWidth: .infinity) }
            } else if state.definitions.isEmpty {
                Section {
                    ContentUnavailableView(
                        "No Definitions",
                        systemImage: "tray",
                        description: Text("No dataset definitions are available.")
                    )
                }
            } else {
                ForEach(state.definitions, id: \.id) { definition in
                    NavigationLink(value: definition) {
                        DefinitionSummaryRow(
                            definition: definition,
                            isDefault: definition.id == state.defaultDefinitionId
                        )
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) { onDelete(definition) } label: {
                            Label("Delete", systemImage: "trash")
                        }
                        Button { onEdit(definition) } label: {
                            Label("Edit", systemImage: "pencil")
                        }
                        .tint(Color.accentColor)
                    }
                    .contextMenu {
                        Button { onEdit(definition) } label: {
                            Label("Edit", systemImage: "pencil")
                        }
                        Button(role: .destructive) { onDelete(definition) } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
        .navigationTitle("Backup")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { onAdd() } label: {
                    Label("Add Definition", systemImage: "plus")
                }
            }
            ToolbarItem(placement: .primaryAction) {
                HelpButton(topic: .backupDefinitions)
            }
        }
        .refreshable { await onRefresh() }
        .alert("Error", isPresented: errorBinding) {
            Button("OK") { onClearError() }
        } message: {
            Text(state.error ?? "")
        }
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { state.error != nil },
            set: { if !$0 { onClearError() } }
        )
    }
}

struct DefinitionSummaryRow: View {
    let definition: DatasetDefinition
    let isDefault: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(definition.info).font(.headline)
                if isDefault {
                    Text("DEFAULT")
                        .font(.caption2.weight(.semibold))
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Color.accentColor.opacity(0.15))
                        .foregroundStyle(Color.accentColor)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }
                Spacer()
                Text(StatusFormatters.shortId(definition.id))
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 12) {
                Label("\(definition.redundantCopies) copies", systemImage: "doc.on.doc")
                Label("Keep: \(RetentionFormatter.shortLabel(definition.existingVersions))", systemImage: "clock.arrow.circlepath")
                Label("Removed: \(RetentionFormatter.shortLabel(definition.removedVersions))", systemImage: "trash.slash")
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .labelStyle(.titleAndIcon)

            HStack(spacing: 12) {
                Text("Created \(definition.created.formatted(date: .abbreviated, time: .omitted))")
                Text("·")
                Text("Updated \(definition.updated.formatted(date: .abbreviated, time: .shortened))")
            }
            .font(.caption2)
            .foregroundStyle(.tertiary)
        }
        .padding(.vertical, 4)
    }
}

#if DEBUG
private struct PreviewHarness: View {
    let state: BackupViewState

    var body: some View {
        NavigationStack {
            BackupViewContent(
                state: state,
                onRefresh: {},
                onClearError: {},
                onAdd: {},
                onEdit: { _ in },
                onDelete: { _ in }
            )
        }
    }
}

#Preview("loading") {
    PreviewHarness(state: .initial)
}

#Preview("no definitions") {
    PreviewHarness(state: BackupViewState(definitions: [], isLoading: false))
}

#Preview("populated") {
    PreviewHarness(state: BackupViewState(
        definitions: [MockServerApiEndpointClient.defaultDefinition, MockServerApiEndpointClient.otherDefinition],
        defaultDefinitionId: MockServerApiEndpointClient.defaultDefinition.id
    ))
}

#Preview("with error") {
    PreviewHarness(state: BackupViewState(
        definitions: [MockServerApiEndpointClient.defaultDefinition],
        defaultDefinitionId: MockServerApiEndpointClient.defaultDefinition.id,
        error: "Access denied"
    ))
}
#endif

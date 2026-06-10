import StasisClientLib
import SwiftUI

struct DefinitionDetailView: View {
    @Environment(AppContainer.self) private var container
    let definition: DatasetDefinition
    @State private var model: DefinitionDetailModel?
    @State private var deletionTarget: DatasetEntry?

    var body: some View {
        DefinitionDetailContent(
            definition: definition,
            state: state,
            onStartBackup: { Task { await model?.startBackup() } },
            onRefresh: { await model?.refresh() },
            onClearError: { model?.clearError() },
            onDeleteEntry: { entry in deletionTarget = entry }
        )
        .navigationDestination(for: DatasetEntry.self) { entry in
            EntryDetailView(entry: entry)
        }
        .task { await loadIfNeeded() }
        .alert("Backup Started", isPresented: didStartBackupBinding) {
            Button("OK") {}
        } message: {
            Text("The backup operation has been started.")
        }
        .confirmationDialog(
            "Delete entry?",
            isPresented: deletionBinding,
            presenting: deletionTarget
        ) { entry in
            Button("Delete", role: .destructive) {
                Task { await model?.deleteEntry(entry.id) }
            }
            Button("Cancel", role: .cancel) {}
        } message: { entry in
            Text("Created \(entry.created.formatted(date: .abbreviated, time: .shortened))")
        }
    }

    private var deletionBinding: Binding<Bool> {
        Binding(
            get: { deletionTarget != nil },
            set: { if !$0 { deletionTarget = nil } }
        )
    }

    private var didStartBackupBinding: Binding<Bool> {
        Binding(
            get: { model?.didStartBackup ?? false },
            set: { if !$0 { model?.didStartBackup = false } }
        )
    }

    private var state: DefinitionDetailViewState {
        guard let model else { return .initial }
        return DefinitionDetailViewState(
            entries: model.entries,
            isLoadingEntries: model.isLoadingEntries,
            startingBackup: model.startingBackup,
            error: model.error
        )
    }

    private func loadIfNeeded() async {
        if model == nil {
            guard let session = container.session else { return }
            model = DefinitionDetailModel(
                session: session,
                ruleRepository: container.ruleRepository,
                definition: definition
            )
        }
        await model?.load()
    }
}

struct DefinitionDetailViewState: Equatable {
    var entries: [DatasetEntry] = []
    var isLoadingEntries: Bool = false
    var startingBackup: Bool = false
    var error: String?

    static let initial = DefinitionDetailViewState(isLoadingEntries: true)
}

private struct DefinitionDetailContent: View {
    let definition: DatasetDefinition
    let state: DefinitionDetailViewState
    let onStartBackup: () -> Void
    let onRefresh: () async -> Void
    let onClearError: () -> Void
    let onDeleteEntry: (DatasetEntry) -> Void

    var body: some View {
        Form {
            summarySection
            entriesSection
        }
        .navigationTitle(definition.info)
        .navigationBarTitleDisplayMode(.inline)
        .refreshable { await onRefresh() }
        .alert("Error", isPresented: errorBinding) {
            Button("OK") { onClearError() }
        } message: {
            Text(state.error ?? "")
        }
        .safeAreaInset(edge: .bottom) {
            startBackupBar
        }
    }

    private var summarySection: some View {
        Section("Summary") {
            LabeledContent("Info", value: definition.info)
            LabeledContent("Id", value: StatusFormatters.shortId(definition.id))
            LabeledContent("Redundant Copies", value: "\(definition.redundantCopies)")
            LabeledContent("Existing Versions", value: retentionDescription(definition.existingVersions))
            LabeledContent("Removed Versions", value: retentionDescription(definition.removedVersions))
            LabeledContent("Created", value: definition.created.formatted(date: .abbreviated, time: .shortened))
            LabeledContent("Updated", value: definition.updated.formatted(date: .abbreviated, time: .shortened))
        }
    }

    @ViewBuilder
    private var entriesSection: some View {
        Section("Entries") {
            if state.isLoadingEntries {
                ProgressView().frame(maxWidth: .infinity)
            } else if state.entries.isEmpty {
                Text("No entries").foregroundStyle(.secondary)
            } else {
                ForEach(state.entries, id: \.id) { entry in
                    NavigationLink(value: entry) {
                        EntrySummaryRow(entry: entry)
                    }
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        Button(role: .destructive) { onDeleteEntry(entry) } label: {
                            Label("Delete", systemImage: "trash")
                        }
                    }
                }
            }
        }
    }

    private var startBackupBar: some View {
        VStack {
            Button {
                onStartBackup()
            } label: {
                if state.startingBackup {
                    ProgressView()
                } else {
                    Text("Start Backup").bold()
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(state.startingBackup)
            .padding()
        }
        .frame(maxWidth: .infinity)
        .background(.bar)
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { state.error != nil },
            set: { if !$0 { onClearError() } }
        )
    }

    private func retentionDescription(_ retention: DatasetDefinition.Retention) -> String {
        let policy: String
        switch retention.policy {
        case .all: policy = "All"
        case .latestOnly: policy = "Latest only"
        case .atMost(let versions): policy = "At most \(versions)"
        }
        return "\(policy), \(StatusFormatters.duration(retention.duration))"
    }
}

struct EntrySummaryRow: View {
    let entry: DatasetEntry

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(entry.created.formatted(date: .abbreviated, time: .shortened))
                    .font(.headline)
                Spacer()
                Text(StatusFormatters.shortId(entry.id))
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
            }
            HStack(spacing: 12) {
                if let size = entry.size {
                    Label(StatusFormatters.bytes(size), systemImage: "internaldrive")
                }
                if let changes = entry.changes {
                    Label("\(changes) changes", systemImage: "arrow.triangle.2.circlepath")
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .labelStyle(.titleAndIcon)
        }
        .padding(.vertical, 4)
    }
}

#if DEBUG
private struct PreviewHarness: View {
    let definition: DatasetDefinition
    let state: DefinitionDetailViewState

    var body: some View {
        NavigationStack {
            DefinitionDetailContent(
                definition: definition,
                state: state,
                onStartBackup: {},
                onRefresh: {},
                onClearError: {},
                onDeleteEntry: { _ in }
            )
        }
    }
}

#Preview("loading") {
    PreviewHarness(
        definition: MockServerApiEndpointClient.defaultDefinition,
        state: .initial
    )
}

#Preview("no entries") {
    PreviewHarness(
        definition: MockServerApiEndpointClient.defaultDefinition,
        state: DefinitionDetailViewState(entries: [], isLoadingEntries: false)
    )
}

#Preview("populated") {
    PreviewHarness(
        definition: MockServerApiEndpointClient.defaultDefinition,
        state: DefinitionDetailViewState(
            entries: [MockServerApiEndpointClient.defaultEntry, MockServerApiEndpointClient.extraEntry],
            isLoadingEntries: false
        )
    )
}

#Preview("starting backup") {
    PreviewHarness(
        definition: MockServerApiEndpointClient.defaultDefinition,
        state: DefinitionDetailViewState(
            entries: [MockServerApiEndpointClient.defaultEntry],
            isLoadingEntries: false,
            startingBackup: true
        )
    )
}

#Preview("with error") {
    PreviewHarness(
        definition: MockServerApiEndpointClient.defaultDefinition,
        state: DefinitionDetailViewState(
            entries: [MockServerApiEndpointClient.defaultEntry],
            isLoadingEntries: false,
            error: "Network error"
        )
    )
}
#endif

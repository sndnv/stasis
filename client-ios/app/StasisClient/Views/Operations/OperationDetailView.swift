import StasisClientLib
import SwiftUI

struct OperationDetailView: View {
    @Environment(AppContainer.self) private var container
    let key: OperationDetailKey
    @State private var model: OperationDetailModel?

    var body: some View {
        OperationDetailContent(
            state: state,
            onClearError: { model?.clearError() }
        )
        .navigationTitle(title)
        .navigationSubtitle(state.definitionInfo ?? "")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                HelpButton(topic: .operationDetails)
            }
        }
        .task { await startIfNeeded() }
    }

    private var state: OperationDetailViewState {
        guard let model else { return .initial(key: key) }
        return OperationDetailViewState(
            key: key,
            backup: model.backup,
            recovery: model.recovery,
            definitionInfo: model.definitionInfo,
            isActive: model.isActive,
            isLoading: model.isLoading,
            error: model.error
        )
    }

    private var title: String {
        switch key.type {
        case .backup: "Backup"
        case .recovery: "Recovery"
        case .expiration: "Expiration"
        case .validation: "Validation"
        case .keyRotation: "Key Rotation"
        case .garbageCollection: "Garbage Collection"
        }
    }

    private func startIfNeeded() async {
        if model == nil {
            guard let session = container.session else { return }
            model = OperationDetailModel(key: key, session: session, trackers: container.trackers)
        }
        await model?.start()
    }
}

struct OperationDetailViewState: Equatable {
    var key: OperationDetailKey
    var backup: BackupState?
    var recovery: RecoveryState?
    var definitionInfo: String?
    var isActive: Bool = false
    var isLoading: Bool = false
    var error: String?

    static func initial(key: OperationDetailKey) -> OperationDetailViewState {
        OperationDetailViewState(key: key, isLoading: true)
    }
}

private struct OperationDetailContent: View {
    let state: OperationDetailViewState
    let onClearError: () -> Void

    @State private var selection: StageSelection?

    var body: some View {
        Form {
            if state.isLoading && state.backup == nil && state.recovery == nil {
                Section { ProgressView().frame(maxWidth: .infinity) }
            } else if let backup = state.backup {
                backupSections(backup)
            } else if let recovery = state.recovery {
                recoverySections(recovery)
            } else {
                Section {
                    ContentUnavailableView(
                        "No State",
                        systemImage: "questionmark.folder",
                        description: Text("No tracked state is available for this operation.")
                    )
                }
            }
        }
        .sheet(item: $selection) { selection in
            StageEntriesSheet(
                title: selection.id,
                description: Self.stageDescriptions[selection.id],
                entries: allStages.first { $0.title == selection.id }?.entries ?? []
            )
        }
        .alert("Error", isPresented: errorBinding) {
            Button("OK") { onClearError() }
        } message: {
            Text(state.error ?? "")
        }
    }

    @ViewBuilder
    private func backupSections(_ backup: BackupState) -> some View {
        Section("Summary") {
            IdLabeledContent("Id", id: state.key.id)
            if let info = state.definitionInfo {
                LabeledContent("Definition", value: info)
            }
            IdLabeledContent("Definition Id", id: backup.definition)
            LabeledContent("Status", value: statusLabel(completed: backup.completed))
            LabeledContent("Started", value: backup.started.formatted(date: .abbreviated, time: .shortened))
            if let completed = backup.completed {
                LabeledContent("Completed", value: completed.formatted(date: .abbreviated, time: .shortened))
            }
        }

        progressSection(progress: backup.asProgress())

        Section("Metadata") {
            LabeledContent(
                "Collected",
                value: backup.metadataCollected?.formatted(date: .abbreviated, time: .shortened) ?? "—"
            )
            LabeledContent(
                "Pushed",
                value: backup.metadataPushed?.formatted(date: .abbreviated, time: .shortened) ?? "—"
            )
        }

        stagesSection()

        if !failureStages.isEmpty {
            failuresSection()
        }
    }

    @ViewBuilder
    private func recoverySections(_ recovery: RecoveryState) -> some View {
        Section("Summary") {
            IdLabeledContent("Id", id: state.key.id)
            LabeledContent("Status", value: statusLabel(completed: recovery.completed))
            LabeledContent("Started", value: recovery.started.formatted(date: .abbreviated, time: .shortened))
            if let completed = recovery.completed {
                LabeledContent("Completed", value: completed.formatted(date: .abbreviated, time: .shortened))
            }
        }

        progressSection(progress: recovery.asProgress())

        stagesSection()

        if !failureStages.isEmpty {
            failuresSection()
        }
    }

    @ViewBuilder
    private func progressSection(progress: OperationProgress) -> some View {
        Section("Progress") {
            LabeledContent("Processed", value: "\(progress.processed) / \(progress.total)")
            if progress.failures > 0 {
                LabeledContent("Failures", value: "\(progress.failures)")
            }
            if progress.total > 0 {
                ProgressView(value: Double(min(progress.processed, progress.total)), total: Double(progress.total))
            }
        }
    }

    @ViewBuilder
    private func stagesSection() -> some View {
        Section("Stages") {
            ForEach(stages) { stage in
                stageRow(stage)
            }
        }
    }

    @ViewBuilder
    private func failuresSection() -> some View {
        Section("Failures") {
            ForEach(failureStages) { stage in
                stageRow(stage)
            }
        }
    }

    private static let stageDescriptions: [String: String] = [
        "Discovered": "Files and directories found based on the configured backup rules or recovery options.",
        "Examined": "Discovered files and directories that have been checked for inclusion in the operation "
            + "- they will be either skipped or collected for further processing.",
        "Skipped": "Examined files and directories that do not need to be processed because they have not changed "
            + "since the last backup or they do not need to be recovered.",
        "Collected": "Examined files and directories that need to be backed up or recovered.",
        "Pending": "Collected files or folders that are being processed.",
        "Processed": "Files and directories that have been processed.",
        "Metadata Applied": "Recovered files and directories that have had their metadata changes applied."
    ]

    private var stages: [Stage] {
        if let backup = state.backup {
            return [
                .keys("Discovered", Array(backup.entities.discovered)),
                .keys("Examined", Array(backup.entities.examined)),
                .keys("Skipped", Array(backup.entities.skipped)),
                .keys("Collected", Array(backup.entities.collected.keys)),
                .progress("Pending", backup.entities.pending) { ($0.processedParts, $0.expectedParts) },
                .progress("Processed", backup.entities.processed) { ($0.processedParts, $0.expectedParts) }
            ]
        } else if let recovery = state.recovery {
            return [
                .keys("Examined", Array(recovery.entities.examined)),
                .keys("Collected", Array(recovery.entities.collected.keys)),
                .progress("Pending", recovery.entities.pending) { ($0.processedParts, $0.expectedParts) },
                .progress("Processed", recovery.entities.processed) { ($0.processedParts, $0.expectedParts) },
                .keys("Metadata Applied", Array(recovery.entities.metadataApplied))
            ]
        }
        return []
    }

    private var failureStages: [Stage] {
        let groups: [Stage]
        if let backup = state.backup {
            groups = [
                Stage(title: "Overall", entries: backup.failures),
                Stage(title: "Per Entity", entries: backup.entities.failed.map { "\($0.key.key): \($0.value)" }.sorted()),
                Stage(title: "Unmatched Rules", entries: backup.entities.unmatched)
            ]
        } else if let recovery = state.recovery {
            groups = [
                Stage(title: "Overall", entries: recovery.failures),
                Stage(title: "Per Entity", entries: recovery.entities.failed.map { "\($0.key.key): \($0.value)" }.sorted())
            ]
        } else {
            groups = []
        }
        return groups.filter { !$0.entries.isEmpty }
    }

    private var allStages: [Stage] { stages + failureStages }

    @ViewBuilder
    private func stageRow(_ stage: Stage) -> some View {
        if stage.entries.isEmpty {
            LabeledContent(stage.title) {
                Text("0").foregroundStyle(.secondary)
            }
        } else {
            Button {
                selection = StageSelection(id: stage.title)
            } label: {
                HStack {
                    Text(stage.title).foregroundStyle(.primary)
                    Spacer()
                    Text("\(stage.entries.count)").foregroundStyle(.secondary)
                    Image(systemName: "chevron.right")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(.tertiary)
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
    }

    private func statusLabel(completed: Date?) -> String {
        if completed != nil { return "Completed" }
        return state.isActive ? "Active" : "Stopped"
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { state.error != nil },
            set: { if !$0 { onClearError() } }
        )
    }

    private struct StageSelection: Identifiable, Hashable {
        let id: String
    }

    private struct Stage: Identifiable {
        var id: String { title }
        let title: String
        let entries: [String]

        static func keys(_ title: String, _ refs: [EntityRef]) -> Stage {
            Stage(title: title, entries: refs.map { $0.key }.sorted())
        }

        static func progress<V>(
            _ title: String,
            _ items: [EntityRef: V],
            parts: (V) -> (processed: Int, expected: Int)
        ) -> Stage {
            let entries = items.sorted { $0.key.key < $1.key.key }.map { ref, value -> String in
                let (processed, expected) = parts(value)
                return expected > 1 ? "\(ref.key) — \(processed) / \(expected)" : ref.key
            }
            return Stage(title: title, entries: entries)
        }
    }
}

private struct StageEntriesSheet: View {
    let title: String
    let description: String?
    let entries: [String]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                if let description {
                    Section {
                        Text(description)
                            .font(.callout)
                            .foregroundStyle(.secondary)
                    }
                }
                if entries.isEmpty {
                    ContentUnavailableView("No Entries", systemImage: "tray")
                } else {
                    ForEach(Array(entries.enumerated()), id: \.offset) { _, entry in
                        Text(entry)
                            .font(.caption.monospaced())
                            .textSelection(.enabled)
                    }
                }
            }
            .navigationTitle(title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Text("\(entries.count)").foregroundStyle(.secondary)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

#if DEBUG
private struct PreviewHarness: View {
    let state: OperationDetailViewState

    var body: some View {
        NavigationStack {
            OperationDetailContent(state: state, onClearError: {})
                .navigationTitle("Backup")
                .navigationBarTitleDisplayMode(.inline)
        }
    }
}

private extension BackupState {
    static func previewActive() -> BackupState {
        BackupState(
            operation: UUID(),
            definition: UUID(),
            started: .now.addingTimeInterval(-300),
            entities: BackupState.Entities(
                discovered: Set([.filesystem(URL(fileURLWithPath: "/a/one.txt")), .filesystem(URL(fileURLWithPath: "/a/two.txt"))]),
                unmatched: [],
                examined: Set([.filesystem(URL(fileURLWithPath: "/a/one.txt"))]),
                skipped: [],
                collected: [:],
                pending: [:],
                processed: [:],
                failed: [:]
            ),
            metadataCollected: nil,
            metadataPushed: nil,
            failures: [],
            completed: nil
        )
    }
}

#Preview("loading") {
    PreviewHarness(state: .initial(key: OperationDetailKey(id: UUID(), type: .backup)))
}

#Preview("active backup") {
    PreviewHarness(state: OperationDetailViewState(
        key: OperationDetailKey(id: UUID(), type: .backup),
        backup: .previewActive(),
        definitionInfo: "Photos",
        isActive: true,
        isLoading: false
    ))
}
#endif

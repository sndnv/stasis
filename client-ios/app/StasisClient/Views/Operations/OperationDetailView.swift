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
        .navigationBarTitleDisplayMode(.inline)
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
        .alert("Error", isPresented: errorBinding) {
            Button("OK") { onClearError() }
        } message: {
            Text(state.error ?? "")
        }
    }

    @ViewBuilder
    private func backupSections(_ backup: BackupState) -> some View {
        Section("Summary") {
            LabeledContent("Id", value: state.key.id.uuidString)
            if let info = state.definitionInfo {
                LabeledContent("Definition", value: info)
            }
            LabeledContent("Definition Id", value: StatusFormatters.shortId(backup.definition))
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

        stagesSection(
            stages: [
                .keys("Discovered", Array(backup.entities.discovered)),
                .keys("Examined", Array(backup.entities.examined)),
                .keys("Skipped", Array(backup.entities.skipped)),
                .keys("Collected", Array(backup.entities.collected.keys)),
                .progress("Pending", backup.entities.pending) { ($0.processedParts, $0.expectedParts) },
                .progress("Processed", backup.entities.processed) { ($0.processedParts, $0.expectedParts) }
            ]
        )

        if !backup.entities.failed.isEmpty || !backup.failures.isEmpty || !backup.entities.unmatched.isEmpty {
            failuresSection(
                unmatched: backup.entities.unmatched,
                entityFailures: backup.entities.failed.map { (ref, reason) in
                    "\(ref.key): \(reason)"
                },
                overallFailures: backup.failures
            )
        }
    }

    @ViewBuilder
    private func recoverySections(_ recovery: RecoveryState) -> some View {
        Section("Summary") {
            LabeledContent("Id", value: state.key.id.uuidString)
            LabeledContent("Status", value: statusLabel(completed: recovery.completed))
            LabeledContent("Started", value: recovery.started.formatted(date: .abbreviated, time: .shortened))
            if let completed = recovery.completed {
                LabeledContent("Completed", value: completed.formatted(date: .abbreviated, time: .shortened))
            }
        }

        progressSection(progress: recovery.asProgress())

        stagesSection(
            stages: [
                .keys("Examined", Array(recovery.entities.examined)),
                .keys("Collected", Array(recovery.entities.collected.keys)),
                .progress("Pending", recovery.entities.pending) { ($0.processedParts, $0.expectedParts) },
                .progress("Processed", recovery.entities.processed) { ($0.processedParts, $0.expectedParts) },
                .keys("Metadata Applied", Array(recovery.entities.metadataApplied))
            ]
        )

        if !recovery.entities.failed.isEmpty || !recovery.failures.isEmpty {
            failuresSection(
                unmatched: [],
                entityFailures: recovery.entities.failed.map { (ref, reason) in
                    "\(ref.key): \(reason)"
                },
                overallFailures: recovery.failures
            )
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
    private func stagesSection(stages: [Stage]) -> some View {
        Section("Stages") {
            ForEach(stages) { stage in
                DisclosureGroup {
                    if stage.entries.isEmpty {
                        Text("None").foregroundStyle(.secondary)
                    } else {
                        ForEach(Array(stage.entries.prefix(Self.maxStageEntries).enumerated()), id: \.offset) { _, entry in
                            Text(entry)
                                .font(.caption.monospaced())
                                .lineLimit(2)
                                .truncationMode(.middle)
                        }
                        if stage.entries.count > Self.maxStageEntries {
                            Text("+\(stage.entries.count - Self.maxStageEntries) more")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                } label: {
                    HStack {
                        Text(stage.title)
                        Spacer()
                        Text("\(stage.entries.count)")
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func failuresSection(
        unmatched: [String],
        entityFailures: [String],
        overallFailures: [String]
    ) -> some View {
        Section("Failures") {
            if !overallFailures.isEmpty {
                DisclosureGroup {
                    ForEach(Array(overallFailures.enumerated()), id: \.offset) { _, failure in
                        Text(failure).font(.caption)
                    }
                } label: {
                    HStack {
                        Text("Overall")
                        Spacer()
                        Text("\(overallFailures.count)").foregroundStyle(.secondary)
                    }
                }
            }
            if !entityFailures.isEmpty {
                DisclosureGroup {
                    ForEach(Array(entityFailures.prefix(Self.maxStageEntries).enumerated()), id: \.offset) { _, failure in
                        Text(failure).font(.caption.monospaced()).lineLimit(3)
                    }
                    if entityFailures.count > Self.maxStageEntries {
                        Text("+\(entityFailures.count - Self.maxStageEntries) more")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                } label: {
                    HStack {
                        Text("Per Entity")
                        Spacer()
                        Text("\(entityFailures.count)").foregroundStyle(.secondary)
                    }
                }
            }
            if !unmatched.isEmpty {
                DisclosureGroup {
                    ForEach(Array(unmatched.enumerated()), id: \.offset) { _, value in
                        Text(value).font(.caption.monospaced()).lineLimit(3)
                    }
                } label: {
                    HStack {
                        Text("Unmatched Rules")
                        Spacer()
                        Text("\(unmatched.count)").foregroundStyle(.secondary)
                    }
                }
            }
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

    private static let maxStageEntries: Int = 50

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

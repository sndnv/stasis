import StasisClientLib
import SwiftUI

struct OperationsView: View {
    @Environment(AppContainer.self) private var container
    @State private var model: OperationsModel?

    var body: some View {
        NavigationStack {
            OperationsViewContent(
                state: state,
                onRefresh: { await model?.refresh() },
                onClearError: { model?.clearError() },
                onStop: { id in Task { await model?.stop(id) } },
                onResume: { id, type in Task { await model?.resume(id, type: type) } },
                onRemove: { id, type in Task { await model?.remove(id, type: type) } }
            )
            .navigationTitle("Operations")
            .navigationDestination(for: OperationDetailKey.self) { key in
                OperationDetailView(key: key)
            }
            .task { await startIfNeeded() }
        }
    }

    private var state: OperationsViewState {
        guard let model else { return .initial }
        return OperationsViewState(
            operations: model.operations,
            isLoading: model.isLoading,
            error: model.error
        )
    }

    private func startIfNeeded() async {
        if model == nil {
            guard let session = container.session else { return }
            model = OperationsModel(session: session, trackers: container.trackers)
        }
        await model?.start()
    }
}

struct OperationsViewState: Equatable {
    var operations: [OperationsModel.Summary] = []
    var isLoading: Bool = false
    var error: String?

    static let initial = OperationsViewState(isLoading: true)
}

private struct OperationsViewContent: View {
    let state: OperationsViewState
    let onRefresh: () async -> Void
    let onClearError: () -> Void
    let onStop: (OperationId) -> Void
    let onResume: (OperationId, OperationType) -> Void
    let onRemove: (OperationId, OperationType) -> Void

    var body: some View {
        List {
            if state.isLoading && state.operations.isEmpty {
                Section { ProgressView().frame(maxWidth: .infinity) }
            } else if state.operations.isEmpty {
                Section {
                    ContentUnavailableView(
                        "No Operations",
                        systemImage: "list.bullet.rectangle",
                        description: Text("Backup and recovery operations will appear here.")
                    )
                }
            } else {
                if !active.isEmpty {
                    Section("Active") {
                        ForEach(active) { summary in row(for: summary) }
                    }
                }
                if !history.isEmpty {
                    Section("History") {
                        ForEach(history) { summary in row(for: summary) }
                    }
                }
            }
        }
        .refreshable { await onRefresh() }
        .alert("Error", isPresented: errorBinding) {
            Button("OK") { onClearError() }
        } message: {
            Text(state.error ?? "")
        }
    }

    private var active: [OperationsModel.Summary] {
        state.operations.filter { $0.status.isActive }
    }

    private var history: [OperationsModel.Summary] {
        state.operations.filter { !$0.status.isActive }
    }

    @ViewBuilder
    private func row(for summary: OperationsModel.Summary) -> some View {
        NavigationLink(value: OperationDetailKey(id: summary.id, type: summary.type)) {
            OperationSummaryRow(summary: summary)
        }
        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
            switch summary.status {
            case .active:
                Button(role: .destructive) { onStop(summary.id) } label: {
                    Label("Stop", systemImage: "stop.fill")
                }
            case .stopped:
                if summary.type == .backup {
                    Button { onResume(summary.id, summary.type) } label: {
                        Label("Resume", systemImage: "play.fill")
                    }
                }
                Button(role: .destructive) { onRemove(summary.id, summary.type) } label: {
                    Label("Remove", systemImage: "trash")
                }
            case .completed:
                Button(role: .destructive) { onRemove(summary.id, summary.type) } label: {
                    Label("Remove", systemImage: "trash")
                }
            }
        }
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { state.error != nil },
            set: { if !$0 { onClearError() } }
        )
    }
}

struct OperationSummaryRow: View {
    let summary: OperationsModel.Summary

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Label(typeLabel, systemImage: typeIcon).font(.headline)
                Spacer()
                statusBadge
            }
            HStack(alignment: .firstTextBaseline) {
                Text(StatusFormatters.shortId(summary.id))
                    .font(.caption.monospaced())
                    .foregroundStyle(.secondary)
                Spacer()
                Text(timestampLabel)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            if let info = summary.definitionInfo {
                Text(info)
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            progressView
        }
        .padding(.vertical, 4)
    }

    private var typeLabel: String {
        switch summary.type {
        case .backup: "Backup"
        case .recovery: "Recovery"
        case .expiration: "Expiration"
        case .validation: "Validation"
        case .keyRotation: "Key rotation"
        case .garbageCollection: "Garbage collection"
        }
    }

    private var typeIcon: String {
        switch summary.type {
        case .backup: "square.and.arrow.up"
        case .recovery: "square.and.arrow.down"
        case .expiration: "hourglass"
        case .validation: "checkmark.shield"
        case .keyRotation: "key"
        case .garbageCollection: "trash"
        }
    }

    @ViewBuilder
    private var statusBadge: some View {
        switch summary.status {
        case .active:
            Label("Active", systemImage: "circle.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.green)
        case .completed:
            Label("Completed", systemImage: "checkmark.circle.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.blue)
        case .stopped:
            Label("Stopped", systemImage: "pause.circle.fill")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.orange)
        }
    }

    private var timestampLabel: String {
        switch summary.status {
        case .completed(let date):
            "Completed \(date.formatted(.relative(presentation: .named)))"
        default:
            "Started \(summary.started.formatted(.relative(presentation: .named)))"
        }
    }

    @ViewBuilder
    private var progressView: some View {
        let progress = summary.progress
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 12) {
                Label("\(progress.processed) / \(progress.total)", systemImage: "checkmark.circle")
                if progress.failures > 0 {
                    Label("\(progress.failures)", systemImage: "exclamationmark.triangle")
                        .foregroundStyle(.red)
                }
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .labelStyle(.titleAndIcon)
            if progress.total > 0 {
                ProgressView(value: Double(min(progress.processed, progress.total)), total: Double(progress.total))
            }
        }
    }
}

#if DEBUG
private struct PreviewHarness: View {
    let state: OperationsViewState

    var body: some View {
        NavigationStack {
            OperationsViewContent(
                state: state,
                onRefresh: {},
                onClearError: {},
                onStop: { _ in },
                onResume: { _, _ in },
                onRemove: { _, _ in }
            )
            .navigationTitle("Operations")
        }
    }
}

private extension OperationsModel.Summary {
    static func mock(
        id: OperationId = UUID(),
        type: OperationType = .backup,
        started: Date = .now.addingTimeInterval(-300),
        progress: OperationProgress,
        status: Status,
        definitionInfo: String? = nil
    ) -> Self {
        OperationsModel.Summary(
            id: id, type: type, started: started, progress: progress,
            status: status, definitionInfo: definitionInfo
        )
    }
}

#Preview("loading") {
    PreviewHarness(state: .initial)
}

#Preview("empty") {
    PreviewHarness(state: OperationsViewState(operations: [], isLoading: false))
}

#Preview("populated") {
    PreviewHarness(state: OperationsViewState(
        operations: [
            .mock(
                type: .backup,
                started: .now.addingTimeInterval(-180),
                progress: OperationProgress(started: .now.addingTimeInterval(-180), total: 100, processed: 42, failures: 0, completed: nil),
                status: .active,
                definitionInfo: "Photos"
            ),
            .mock(
                type: .recovery,
                started: .now.addingTimeInterval(-3600),
                progress: OperationProgress(
                    started: .now.addingTimeInterval(-3600),
                    total: 50, processed: 50, failures: 2,
                    completed: .now.addingTimeInterval(-3000)
                ),
                status: .completed(.now.addingTimeInterval(-3000))
            ),
            .mock(
                type: .backup,
                started: .now.addingTimeInterval(-86400),
                progress: OperationProgress(
                    started: .now.addingTimeInterval(-86400),
                    total: 200, processed: 180, failures: 1, completed: nil
                ),
                status: .stopped,
                definitionInfo: "Documents"
            )
        ],
        isLoading: false
    ))
}

#Preview("with error") {
    PreviewHarness(state: OperationsViewState(
        operations: [],
        isLoading: false,
        error: "Failed to load operations"
    ))
}
#endif

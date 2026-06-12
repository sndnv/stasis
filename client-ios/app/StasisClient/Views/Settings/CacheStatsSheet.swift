import StasisClientLib
import SwiftUI

struct CacheStatsSheet: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.dismiss) private var dismiss
    @State private var model: CacheStatsModel?

    var body: some View {
        NavigationStack {
            CacheStatsContent(
                state: state,
                onRefresh: { await model?.refresh() }
            )
            .navigationTitle("Cache Statistics")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .task { await loadIfNeeded() }
        }
    }

    private var state: CacheStatsViewState {
        guard let model else { return .initial }
        return CacheStatsViewState(stats: model.stats, isLoading: model.isLoading)
    }

    private func loadIfNeeded() async {
        if model == nil {
            model = CacheStatsModel(
                session: container.session,
                scheduler: container.backgroundScheduler
            )
        }
        await model?.refresh()
    }
}

struct CacheStatsViewState: Equatable {
    var stats: [CacheStat] = []
    var isLoading: Bool = false

    static let initial = CacheStatsViewState(isLoading: true)
}

private struct CacheStatsContent: View {
    let state: CacheStatsViewState
    let onRefresh: () async -> Void

    var body: some View {
        List {
            if state.isLoading && state.stats.isEmpty {
                Section { ProgressView().frame(maxWidth: .infinity) }
            } else if state.stats.isEmpty {
                Section {
                    ContentUnavailableView(
                        "No Caches",
                        systemImage: "tray",
                        description: Text("No tracked caches are available.")
                    )
                }
            } else {
                ForEach(state.stats) { stat in
                    CacheStatRow(stat: stat)
                }
            }
        }
        .refreshable { await onRefresh() }
    }
}

struct CacheStatRow: View {
    let stat: CacheStat

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(stat.name).font(.headline)
            HStack(spacing: 12) {
                Label("\(stat.entries) entries", systemImage: "tray.full")
                Label("\(stat.hits) hits", systemImage: "checkmark.circle")
                Label("\(stat.misses) misses", systemImage: "xmark.circle")
            }
            .font(.caption)
            .foregroundStyle(.secondary)
            .labelStyle(.titleAndIcon)
            if hasReadActivity {
                Text("Reads: \(operationsLabel(stat.readStatistics))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
            if hasWriteActivity {
                Text("Writes: \(operationsLabel(stat.writeStatistics))")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }

    private var hasReadActivity: Bool { stat.readStatistics.operations > 0 }
    private var hasWriteActivity: Bool { stat.writeStatistics.operations > 0 }

    private func operationsLabel(_ stats: OperationStatistics) -> String {
        let bytes = StatusFormatters.bytes(stats.bytesProcessed)
        return "\(stats.operations) ops · \(bytes) · \(stats.minDuration)–\(stats.maxDuration) ms"
    }
}

#if DEBUG
private struct PreviewHarness: View {
    let state: CacheStatsViewState

    var body: some View {
        NavigationStack {
            CacheStatsContent(state: state, onRefresh: {})
                .navigationTitle("Cache Statistics")
                .navigationBarTitleDisplayMode(.inline)
        }
    }
}

#Preview("loading") {
    PreviewHarness(state: .initial)
}

#Preview("populated") {
    PreviewHarness(state: CacheStatsViewState(
        stats: [
            CacheStat(
                id: "Dataset Definitions",
                name: "Dataset Definitions",
                entries: 12,
                hits: 30,
                misses: 4,
                readStatistics: .empty(),
                writeStatistics: .empty()
            ),
            CacheStat(
                id: "Dataset Metadata",
                name: "Dataset Metadata",
                entries: 5,
                hits: 8,
                misses: 2,
                readStatistics: OperationStatistics(
                    bytesProcessed: 4096, minDuration: 1, maxDuration: 12, operations: 10
                ),
                writeStatistics: OperationStatistics(
                    bytesProcessed: 4096, minDuration: 3, maxDuration: 15, operations: 5
                )
            )
        ],
        isLoading: false
    ))
}
#endif

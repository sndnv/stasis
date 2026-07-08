import StasisClientLib
import SwiftUI

struct SearchView: View {
    @Environment(AppContainer.self) private var container
    @State private var model: SearchModel?
    @State private var query: String = ""
    @State private var until: Date = .now

    var body: some View {
        NavigationStack {
            SearchViewContent(
                state: state,
                query: $query,
                until: $until,
                onRunSearch: { await runSearch() },
                onClearError: { model?.clearError() }
            )
            .navigationTitle("Search")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    HelpButton(topic: .search)
                }
            }
            .navigationDestination(for: DatasetDefinitionResult.self) { result in
                SearchMatchesView(result: result)
            }
            .task { initIfNeeded() }
        }
    }

    private var state: SearchViewState {
        guard let model else { return .initial }
        return SearchViewState(
            status: model.status,
            lastQuery: model.lastQuery,
            results: model.result?.nonEmptyDefinitions ?? [],
            hasResult: model.result != nil,
            error: model.error
        )
    }

    private func runSearch() async {
        await model?.run(query: query, until: until)
    }

    private func initIfNeeded() {
        if model == nil {
            guard let session = container.session else { return }
            model = SearchModel(session: session)
        }
    }
}

struct SearchViewState: Equatable {
    var status: SearchModel.Status = .idle
    var lastQuery: String = ""
    var results: [DefinitionSearchRow] = []
    var hasResult: Bool = false
    var error: String?

    static let initial = SearchViewState()
}

private struct SearchViewContent: View {
    @Environment(ToastCenter.self) private var toasts
    let state: SearchViewState
    @Binding var query: String
    @Binding var until: Date
    let onRunSearch: () async -> Void
    let onClearError: () -> Void

    var body: some View {
        Form {
            querySection
            untilSection
            runSection
            resultsSection
        }
        .alert("Error", isPresented: errorBinding) {
            Button("OK") { onClearError() }
        } message: {
            Text(state.error ?? "")
        }
    }

    private var querySection: some View {
        Section {
            TextField("Search files", text: $query)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        } header: {
            Text("Query")
        } footer: {
            Text("Treated as a regular expression. Plain text matches literally.")
        }
    }

    private var untilSection: some View {
        Section {
            DatePicker("Until", selection: $until, displayedComponents: [.date, .hourAndMinute])
        } footer: {
            Text("Searches the latest backup entry created before this date.")
        }
    }

    private var runSection: some View {
        Section {
            Button {
                Task { await onRunSearch() }
            } label: {
                if state.status == .running {
                    HStack { Spacer(); ProgressView(); Spacer() }
                } else {
                    Text("Run Search").bold().frame(maxWidth: .infinity)
                }
            }
            .disabled(query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || state.status == .running)
        }
    }

    @ViewBuilder
    private var resultsSection: some View {
        if state.hasResult {
            Section("Results") {
                if state.results.isEmpty {
                    Text("No matches for \"\(state.lastQuery)\".")
                        .foregroundStyle(.secondary)
                } else {
                    ForEach(state.results) { row in
                        NavigationLink(value: row.result) {
                            SearchResultRow(result: row.result)
                        }
                        .contextMenu {
                            Button {
                                copy(row.definition, label: "definition ID")
                            } label: {
                                Label("Copy definition ID", systemImage: "doc.on.doc")
                            }
                            Button {
                                copy(row.result.entryId, label: "entry ID")
                            } label: {
                                Label("Copy entry ID", systemImage: "doc.on.doc")
                            }
                        }
                    }
                }
            }
        }
    }

    private func copy(_ id: UUID, label: String) {
        UIPasteboard.general.string = id.uuidString.lowercased()
        toasts.show("Copied \(label)")
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { state.error != nil },
            set: { if !$0 { onClearError() } }
        )
    }
}

struct SearchResultRow: View {
    let result: DatasetDefinitionResult

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(alignment: .firstTextBaseline) {
                Text(result.definitionInfo).font(.headline)
                Spacer()
                Text("\(result.matches.count)")
                    .font(.caption.monospaced())
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Color.accentColor.opacity(0.15))
                    .clipShape(Capsule())
            }
            HStack(spacing: 12) {
                Label(StatusFormatters.shortId(result.entryId), systemImage: "doc")
                    .labelStyle(.titleAndIcon)
                Text(result.entryCreated.formatted(date: .abbreviated, time: .shortened))
            }
            .font(.caption)
            .foregroundStyle(.secondary)
        }
        .padding(.vertical, 4)
    }
}

#if DEBUG
private struct PreviewHarness: View {
    let state: SearchViewState
    @State private var query: String
    @State private var until: Date = .now

    init(state: SearchViewState, query: String = "") {
        self.state = state
        _query = State(initialValue: query)
    }

    var body: some View {
        NavigationStack {
            SearchViewContent(
                state: state,
                query: $query,
                until: $until,
                onRunSearch: {},
                onClearError: {}
            )
            .navigationTitle("Search")
        }
        .environment(ToastCenter(displayDuration: .seconds(2.5)))
    }
}

private extension DatasetDefinitionResult {
    static func mock() -> DatasetDefinitionResult {
        DatasetDefinitionResult(
            definitionInfo: "Photos",
            entryId: UUID(),
            entryCreated: .now.addingTimeInterval(-3600),
            matches: [
                "/photos/2026/sunset.jpg": .new,
                "/photos/2026/beach.jpg": .existing(entry: UUID()),
                "/photos/2025/portrait.jpg": .updated
            ]
        )
    }
}

#Preview("idle") {
    PreviewHarness(state: .initial)
}

#Preview("results") {
    PreviewHarness(
        state: SearchViewState(
            status: .completed,
            lastQuery: "jpg",
            results: [DefinitionSearchRow(definition: UUID(), result: .mock())],
            hasResult: true
        ),
        query: "jpg"
    )
}

#Preview("empty results") {
    PreviewHarness(
        state: SearchViewState(
            status: .completed,
            lastQuery: "nomatches",
            results: [],
            hasResult: true
        ),
        query: "nomatches"
    )
}

#Preview("running") {
    PreviewHarness(
        state: SearchViewState(status: .running, lastQuery: "jpg"),
        query: "jpg"
    )
}
#endif

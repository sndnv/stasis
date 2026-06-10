import StasisClientLib
import SwiftUI

struct RecoverView: View {
    @Environment(AppContainer.self) private var container
    @State private var model: RecoverModel?
    @State private var config: RecoverConfig = .initial

    var body: some View {
        NavigationStack {
            RecoverViewContent(
                state: state,
                config: $config,
                onRefresh: { await model?.refresh() },
                onClearError: { model?.clearError() },
                onDefinitionChange: { id in await handleDefinitionChange(id) },
                onRunRecover: { await model?.startRecovery(config: config) }
            )
            .navigationTitle("Recover")
            .task { await loadIfNeeded() }
            .alert("Recovery Started", isPresented: didStartRecoveryBinding) {
                Button("OK") {}
            } message: {
                Text("The recovery operation has been started.")
            }
        }
    }

    private var didStartRecoveryBinding: Binding<Bool> {
        Binding(
            get: { model?.didStartRecovery ?? false },
            set: { if !$0 { model?.didStartRecovery = false } }
        )
    }

    private var state: RecoverViewState {
        guard let model else { return .initial }
        return RecoverViewState(
            definitions: model.definitions,
            entries: model.entries,
            isLoadingDefinitions: model.isLoadingDefinitions,
            isLoadingEntries: model.isLoadingEntries,
            startingRecovery: model.startingRecovery,
            error: model.error
        )
    }

    private func loadIfNeeded() async {
        if model == nil {
            guard let session = container.session else { return }
            model = RecoverModel(session: session)
        }
        await model?.load()
    }

    private func handleDefinitionChange(_ id: DatasetDefinitionId?) async {
        if let id {
            await model?.loadEntriesIfNeeded(for: id)
        } else {
            model?.resetEntries()
        }
    }
}

struct RecoverViewState: Equatable {
    var definitions: [DatasetDefinition] = []
    var entries: [DatasetEntry] = []
    var isLoadingDefinitions: Bool = false
    var isLoadingEntries: Bool = false
    var startingRecovery: Bool = false
    var error: String?

    static let initial = RecoverViewState(isLoadingDefinitions: true)
}

private struct RecoverViewContent: View {
    let state: RecoverViewState
    @Binding var config: RecoverConfig
    let onRefresh: () async -> Void
    let onClearError: () -> Void
    let onDefinitionChange: (DatasetDefinitionId?) async -> Void
    let onRunRecover: () async -> Void

    @State private var showMoreOptions: Bool = false
    @State private var untilDate: Date = Date()

    var body: some View {
        Form {
            definitionSection
            if config.definition != nil {
                sourceSection
                pathQuerySection
                moreOptionsSection
            }
        }
        .refreshable { await onRefresh() }
        .alert("Error", isPresented: errorBinding) {
            Button("OK") { onClearError() }
        } message: {
            Text(state.error ?? "")
        }
        .safeAreaInset(edge: .bottom) {
            runRecoverBar
        }
    }

    @ViewBuilder
    private var definitionSection: some View {
        Section("Definition") {
            if state.isLoadingDefinitions && state.definitions.isEmpty {
                ProgressView().frame(maxWidth: .infinity)
            } else if state.definitions.isEmpty {
                Text("No definitions with entries available.").foregroundStyle(.secondary)
            } else {
                Picker("Definition", selection: definitionBinding) {
                    Text("None").tag(DatasetDefinitionId?.none)
                    ForEach(state.definitions, id: \.id) { definition in
                        Text(definitionLabel(definition)).tag(Optional(definition.id))
                    }
                }
                .pickerStyle(.menu)
            }
        }
    }

    @ViewBuilder
    private var sourceSection: some View {
        Section("Source") {
            Picker("Type", selection: sourceKindBinding) {
                ForEach(RecoverConfig.RecoverySource.Kind.allCases) { kind in
                    Text(kind.label).tag(kind)
                }
            }
            .pickerStyle(.segmented)

            switch config.recoverySource {
            case .latest:
                Text("Recovers the latest entry for this definition.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            case .entry:
                entryPicker
            case .until:
                DatePicker("Until", selection: untilBinding, displayedComponents: [.date, .hourAndMinute])
            }
        }
    }

    @ViewBuilder
    private var entryPicker: some View {
        if state.isLoadingEntries && state.entries.isEmpty {
            ProgressView().frame(maxWidth: .infinity)
        } else if state.entries.isEmpty {
            Text("No entries available.").foregroundStyle(.secondary)
        } else {
            Picker("Entry", selection: entryBinding) {
                Text("None").tag(DatasetEntryId?.none)
                ForEach(state.entries, id: \.id) { entry in
                    Text(entryLabel(entry)).tag(Optional(entry.id))
                }
            }
            .pickerStyle(.menu)
        }
    }

    private var pathQuerySection: some View {
        Section {
            TextField("Path query (regex)", text: $config.pathQuery)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        } header: {
            Text("Path Query")
        } footer: {
            Text("Use a regex to filter restored files by path. Empty restores all.")
        }
    }

    private var moreOptionsSection: some View {
        Section {
            DisclosureGroup("More Options", isExpanded: $showMoreOptions) {
                TextField("Destination directory", text: $config.destination)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Toggle("Discard original paths", isOn: $config.discardPaths)
                    .disabled(config.destination.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        } footer: {
            Text("Leave the destination empty to restore files to their original locations.")
        }
    }

    private var runRecoverBar: some View {
        VStack {
            Button {
                Task { await onRunRecover() }
            } label: {
                if state.startingRecovery {
                    ProgressView()
                } else {
                    Text(config.validate().buttonLabel).bold()
                }
            }
            .buttonStyle(.borderedProminent)
            .disabled(config.validate() != .valid || state.startingRecovery)
            .padding()
        }
        .frame(maxWidth: .infinity)
        .background(.bar)
    }

    private var definitionBinding: Binding<DatasetDefinitionId?> {
        Binding(
            get: { config.definition },
            set: { newValue in
                config.definition = newValue
                config.recoverySource = .latest
                Task { await onDefinitionChange(newValue) }
            }
        )
    }

    private var sourceKindBinding: Binding<RecoverConfig.RecoverySource.Kind> {
        Binding(
            get: { config.recoverySource.kind },
            set: { kind in
                switch kind {
                case .latest: config.recoverySource = .latest
                case .entry: config.recoverySource = .entry(nil)
                case .until: config.recoverySource = .until(untilDate)
                }
            }
        )
    }

    private var entryBinding: Binding<DatasetEntryId?> {
        Binding(
            get: {
                if case .entry(let id) = config.recoverySource { return id }
                return nil
            },
            set: { config.recoverySource = .entry($0) }
        )
    }

    private var untilBinding: Binding<Date> {
        Binding(
            get: {
                if case .until(let date) = config.recoverySource { return date }
                return untilDate
            },
            set: { newValue in
                untilDate = newValue
                config.recoverySource = .until(newValue)
            }
        )
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { state.error != nil },
            set: { if !$0 { onClearError() } }
        )
    }

    private func definitionLabel(_ definition: DatasetDefinition) -> String {
        "\(definition.info) · \(StatusFormatters.shortId(definition.id))"
    }

    private func entryLabel(_ entry: DatasetEntry) -> String {
        let date = entry.created.formatted(date: .abbreviated, time: .shortened)
        return "\(date) · \(StatusFormatters.shortId(entry.id))"
    }
}

#if DEBUG
private struct PreviewHarness: View {
    let state: RecoverViewState
    @State private var config: RecoverConfig

    init(state: RecoverViewState, config: RecoverConfig = .initial) {
        self.state = state
        _config = State(initialValue: config)
    }

    var body: some View {
        NavigationStack {
            RecoverViewContent(
                state: state,
                config: $config,
                onRefresh: {},
                onClearError: {},
                onDefinitionChange: { _ in },
                onRunRecover: {}
            )
            .navigationTitle("Recover")
        }
    }
}

#Preview("loading") {
    PreviewHarness(state: .initial)
}

#Preview("no definitions") {
    PreviewHarness(state: RecoverViewState(definitions: [], isLoadingDefinitions: false))
}

#Preview("populated") {
    PreviewHarness(state: RecoverViewState(
        definitions: [
            MockServerApiEndpointClient.defaultDefinition,
            MockServerApiEndpointClient.otherDefinition
        ],
        isLoadingDefinitions: false
    ))
}

#Preview("with selected definition") {
    PreviewHarness(
        state: RecoverViewState(
            definitions: [
                MockServerApiEndpointClient.defaultDefinition,
                MockServerApiEndpointClient.otherDefinition
            ],
            entries: [MockServerApiEndpointClient.defaultEntry, MockServerApiEndpointClient.extraEntry],
            isLoadingDefinitions: false
        ),
        config: RecoverConfig(
            definition: MockServerApiEndpointClient.defaultDefinition.id,
            recoverySource: .entry(MockServerApiEndpointClient.defaultEntry.id),
            pathQuery: "",
            destination: "",
            discardPaths: false
        )
    )
}

#Preview("with error") {
    PreviewHarness(state: RecoverViewState(
        definitions: [MockServerApiEndpointClient.defaultDefinition],
        isLoadingDefinitions: false,
        error: "Network error"
    ))
}
#endif

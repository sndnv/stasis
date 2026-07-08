import StasisClientLib
import SwiftUI

struct EntryDetailView: View {
    @Environment(AppContainer.self) private var container
    let entry: DatasetEntry
    @State private var model: EntryDetailModel?
    @State private var filters: EntryMetadataFilters

    init(entry: DatasetEntry, initialFilters: EntryMetadataFilters = .default) {
        self.entry = entry
        _filters = State(initialValue: initialFilters)
    }

    var body: some View {
        EntryDetailContent(
            entry: entry,
            state: state,
            session: container.session,
            filters: $filters,
            onRefresh: { await model?.refresh() },
            onClearError: { model?.clearError() }
        )
        .task { await loadIfNeeded() }
    }

    private var state: EntryDetailViewState {
        guard let model else { return .initial }
        return EntryDetailViewState(
            metadata: model.metadata,
            isLoading: model.isLoading,
            error: model.error
        )
    }

    private func loadIfNeeded() async {
        if model == nil {
            guard let session = container.session else { return }
            model = EntryDetailModel(session: session, entry: entry)
        }
        await model?.load()
    }
}

struct EntryDetailViewState: Equatable {
    var metadata: DatasetMetadata?
    var isLoading: Bool = false
    var error: String?

    static let initial = EntryDetailViewState(isLoading: true)
}

struct EntryMetadataFilters: Equatable {
    enum KindFilter: Hashable {
        case all
        case scheme(String?)
    }

    var updatesOnly: Bool = true
    var filesOnly: Bool = true
    var noHidden: Bool = true
    var pathQuery: String = ""
    var exactPath: Bool = false
    var kind: KindFilter = .all

    static let `default` = EntryMetadataFilters()

    func withoutKind() -> EntryMetadataFilters {
        var copy = self
        copy.kind = .all
        return copy
    }

    func apply(to entries: [PathEntry]) -> [PathEntry] {
        let query = pathQuery.trimmingCharacters(in: .whitespaces).lowercased()
        return entries.filter { entry in
            if updatesOnly, case .existing = entry.state { return false }
            if filesOnly, case .directory = entry.metadata { return false }
            if noHidden, EntryMetadataFilters.isPathHidden(entry: entry) { return false }
            if case .scheme(let scheme) = kind, SourceUri.scheme(entry.path) != scheme { return false }
            if !query.isEmpty {
                let path = entry.path.lowercased()
                if exactPath {
                    if path != query { return false }
                } else if !path.contains(query) {
                    return false
                }
            }
            return true
        }
    }

    private static func isPathHidden(entry: PathEntry) -> Bool {
        if entry.metadata.filesystem?.isHidden == true { return true }
        let lastComponent = (entry.path as NSString).lastPathComponent
        return lastComponent.hasPrefix(".") || lastComponent.hasSuffix(".tmp")
    }
}

struct PathEntry: Identifiable, Equatable {
    enum Kind: Equatable { case content, metadata }
    let path: String
    let metadata: EntityMetadata
    let state: FilesystemMetadata.EntityState
    let kind: Kind
    var id: String { "\(kind)/\(path)" }
}

struct EntryPathBuckets: Equatable {
    let content: [PathEntry]
    let metadata: [PathEntry]
    var totalCount: Int { content.count + metadata.count }
}

extension DatasetMetadata {
    func pathEntries() -> EntryPathBuckets {
        let content = contentChanged.map { path, meta in
            PathEntry(path: path, metadata: meta, state: filesystem.get(path) ?? .updated, kind: .content)
        }.sorted { $0.path < $1.path }
        let meta = metadataChanged.map { path, meta in
            PathEntry(path: path, metadata: meta, state: filesystem.get(path) ?? .updated, kind: .metadata)
        }.sorted { $0.path < $1.path }
        return EntryPathBuckets(content: content, metadata: meta)
    }
}

private struct EntryDetailContent: View {
    let entry: DatasetEntry
    let state: EntryDetailViewState
    let session: AuthenticatedSession?
    @Binding var filters: EntryMetadataFilters
    let onRefresh: () async -> Void
    let onClearError: () -> Void

    @State private var filtersExpanded: Bool = false
    @State private var sheetEntry: PathEntry?

    var body: some View {
        Form {
            entrySection
            if state.isLoading {
                Section { ProgressView().frame(maxWidth: .infinity) }
            } else if let metadata = state.metadata {
                metadataBody(metadata: metadata)
            } else {
                Section {
                    Text("Metadata unavailable").foregroundStyle(.secondary)
                }
            }
        }
        .navigationTitle("Entry")
        .navigationSubtitle(entry.created.formatted(date: .abbreviated, time: .shortened))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                HelpButton(topic: .entryDetails)
            }
        }
        .refreshable { await onRefresh() }
        .alert("Error", isPresented: errorBinding) {
            Button("OK") { onClearError() }
        } message: {
            Text(state.error ?? "")
        }
        .sheet(item: $sheetEntry) { entry in
            EntityMetadataSheet(entry: entry, session: session)
        }
    }

    private var entrySection: some View {
        Section("Entry") {
            IdLabeledContent("Id", id: entry.id)
            LabeledContent("Created", value: entry.created.formatted(date: .abbreviated, time: .shortened))
            if let size = entry.size {
                LabeledContent("Size", value: StatusFormatters.bytes(size))
            }
            if let changes = entry.changes {
                LabeledContent("Changes", value: "\(changes)")
            }
            LabeledContent("Crates", value: "\(entry.data.count)")
        }
    }

    @ViewBuilder
    private func metadataBody(metadata: DatasetMetadata) -> some View {
        let buckets = metadata.pathEntries()
        let nonKind = filters.withoutKind().apply(to: buckets.content + buckets.metadata)
        let schemes = Self.presentSchemes(nonKind)
        let counts = Self.schemeCounts(nonKind)
        let filteredContent = filters.apply(to: buckets.content)
        let filteredMeta = filters.apply(to: buckets.metadata)
        let shownCount = filteredContent.count + filteredMeta.count

        filtersSection(shown: shownCount, total: buckets.totalCount)
        if schemes.count > 1 {
            kindChipsSection(schemes: schemes, counts: counts)
        }
        if !filteredContent.isEmpty {
            pathSection(title: "Content Changed", entries: filteredContent, onTap: { sheetEntry = $0 })
        }
        if !filteredMeta.isEmpty {
            pathSection(title: "Metadata Changed", entries: filteredMeta, onTap: { sheetEntry = $0 })
        }
        if shownCount == 0 && buckets.totalCount > 0 {
            Section {
                Text("No items match the filters").foregroundStyle(.secondary)
            }
        }
    }

    private func kindChipsSection(schemes: [String?], counts: [EntryMetadataFilters.KindFilter: Int]) -> some View {
        Section {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    kindChip(filter: .all, label: "All", count: counts[.all] ?? 0)
                    ForEach(schemes, id: \.self) { scheme in
                        kindChip(
                            filter: .scheme(scheme),
                            label: Self.schemeLabel(scheme),
                            count: counts[.scheme(scheme)] ?? 0
                        )
                    }
                }
                .padding(.vertical, 4)
            }
            .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))
        }
    }

    private func kindChip(filter: EntryMetadataFilters.KindFilter, label: String, count: Int) -> some View {
        let isSelected = filters.kind == filter
        return Button {
            filters.kind = filter
        } label: {
            Text("\(label) (\(count))")
                .font(.caption.weight(.medium))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? Color.accentColor : Color.secondary.opacity(0.15))
                .foregroundStyle(isSelected ? Color.white : Color.primary)
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private static func presentSchemes(_ entries: [PathEntry]) -> [String?] {
        var hasFiles = false
        var seen = Set<String>()
        var ordered: [String] = []
        for entry in entries {
            if let scheme = SourceUri.scheme(entry.path) {
                if seen.insert(scheme).inserted { ordered.append(scheme) }
            } else {
                hasFiles = true
            }
        }
        let known = LibrarySource.all.map(\.scheme)
        let libraries = known.filter { ordered.contains($0) }
        let others = ordered.filter { !known.contains($0) }.sorted()
        let files: [String?] = hasFiles ? [String?.none] : []
        return files + (libraries + others).map { Optional($0) }
    }

    private static func schemeCounts(_ entries: [PathEntry]) -> [EntryMetadataFilters.KindFilter: Int] {
        var counts: [EntryMetadataFilters.KindFilter: Int] = [.all: entries.count]
        for entry in entries {
            counts[.scheme(SourceUri.scheme(entry.path)), default: 0] += 1
        }
        return counts
    }

    private static func schemeLabel(_ scheme: String?) -> String {
        guard let scheme else { return "Files" }
        return LibrarySource.all.first { $0.scheme == scheme }?.displayName ?? scheme
    }

    private func filtersSection(shown: Int, total: Int) -> some View {
        Section {
            DisclosureGroup(isExpanded: $filtersExpanded) {
                Toggle("Updates Only", isOn: $filters.updatesOnly)
                Toggle("Files Only", isOn: $filters.filesOnly)
                Toggle("No Hidden", isOn: $filters.noHidden)
                TextField("Path contains", text: $filters.pathQuery)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
            } label: {
                Text("Showing \(shown) of \(total) items")
                    .font(.callout)
            }
        }
    }

    private func pathSection(title: String, entries: [PathEntry], onTap: @escaping (PathEntry) -> Void) -> some View {
        Section(title) {
            ForEach(entries) { entry in
                Button { onTap(entry) } label: {
                    MetadataRowView(entry: entry)
                }
                .buttonStyle(.plain)
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

private struct MetadataRowView: View {
    let entry: PathEntry

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: EntryDisplay.kindIcon(path: entry.path, metadata: entry.metadata))
                .font(.title3)
                .foregroundStyle(stateColor)
                .frame(width: 22)
            VStack(alignment: .leading, spacing: 2) {
                Text(EntryDisplay.displayName(path: entry.path, metadata: entry.metadata))
                    .font(.callout).bold()
                    .lineLimit(1)
                    .truncationMode(.middle)
                if !secondary.isEmpty {
                    Text(secondary)
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
                HStack(spacing: 8) {
                    Text(EntryDisplay.kindLabel(path: entry.path, metadata: entry.metadata))
                    if let content = entry.metadata.content {
                        Text(StatusFormatters.bytes(content.size))
                    }
                    Text(stateLabel).foregroundStyle(stateColor)
                }
                .font(.caption)
                .foregroundStyle(.secondary)
            }
            Spacer()
            Image(systemName: "chevron.right")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.tertiary)
        }
        .contentShape(.rect)
    }

    private var secondary: String {
        EntryDisplay.secondary(path: entry.path, metadata: entry.metadata)
    }

    private var stateColor: Color {
        switch entry.state {
        case .new: .green
        case .updated: .orange
        case .existing: .secondary
        }
    }

    private var stateLabel: String {
        switch entry.state {
        case .new: "New"
        case .updated: "Updated"
        case .existing: "Existing"
        }
    }
}

private struct EntityMetadataSheet: View {
    let entry: PathEntry
    let session: AuthenticatedSession?
    @Environment(\.dismiss) private var dismiss
    @State private var model: EntryContentModel?
    @State private var shareItem: ShareItem?
    @State private var actionError: String?
    @State private var pendingConfirmation: String?
    @State private var toasts = ToastCenter(displayDuration: .seconds(2.5))

    @MainActor
    init(entry: PathEntry, session: AuthenticatedSession?) {
        self.entry = entry
        self.session = session
        let contentModel: EntryContentModel? = {
            guard let session, entry.metadata.content != nil else { return nil }
            return EntryContentModel.live(
                session: session,
                entityKey: entry.path,
                displayName: EntryDisplay.displayName(path: entry.path, metadata: entry.metadata),
                metadata: entry.metadata
            )
        }()
        _model = State(initialValue: contentModel)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("Path") {
                    LabeledContent("Full Path", value: entry.path)
                        .textSelection(.enabled)
                    if let link = entry.metadata.filesystem?.link {
                        LabeledContent("Link", value: link).textSelection(.enabled)
                    }
                    LabeledContent("Kind", value: kindLabel)
                    LabeledContent("State", value: stateLabel)
                    LabeledContent("Change", value: entry.kind == .content ? "Content" : "Metadata")
                }
                Section("Attributes") {
                    if let filesystem = entry.metadata.filesystem {
                        LabeledContent("Hidden", value: filesystem.isHidden ? "Yes" : "No")
                        LabeledContent("Owner", value: filesystem.owner)
                        LabeledContent("Group", value: filesystem.group)
                        LabeledContent("Permissions", value: filesystem.permissions)
                    }
                    LabeledContent("Created", value: entry.metadata.created.formatted(date: .abbreviated, time: .shortened))
                    LabeledContent("Updated", value: entry.metadata.updated.formatted(date: .abbreviated, time: .shortened))
                }
                if let content = entry.metadata.content {
                    contentInfoSection(content: content)
                }
                if let model {
                    contentSection(model: model)
                }
            }
            .navigationTitle(EntryDisplay.displayName(path: entry.path, metadata: entry.metadata))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(item: $shareItem) { item in
                ShareSheet(url: item.url) { completed in
                    if completed, let message = pendingConfirmation {
                        toasts.show(message)
                    }
                    pendingConfirmation = nil
                }
            }
            .alert("Error", isPresented: errorBinding) {
                Button("OK") { actionError = nil }
            } message: {
                Text(actionError ?? "")
            }
            .toastLayer()
            .environment(toasts)
        }
        .presentationDetents([.medium, .large])
    }

    private func contentSection(model: EntryContentModel) -> some View {
        Section("Content") {
            NavigationLink("Preview") { EntryContentPreviewView(model: model) }
            Button("Save…") { save(model) }
            if model.canExport {
                Button(exportLabel(model)) { export(model) }
            }
        }
    }

    private func save(_ model: EntryContentModel) {
        Task { @MainActor in
            do {
                let bytes = try await model.rawContent()
                let url = try ExportFile.write(
                    name: model.displayName,
                    fileExtension: ExportFile.inferExtension(bytes),
                    bytes: bytes
                )
                pendingConfirmation = "Saved"
                shareItem = ShareItem(url: url)
            } catch {
                actionError = error.localizedDescription
            }
        }
    }

    private func export(_ model: EntryContentModel) {
        Task { @MainActor in
            do {
                let content = try await model.exportedContent()
                let url = try ExportFile.write(
                    name: model.displayName,
                    fileExtension: content.fileExtension,
                    bytes: content.bytes
                )
                pendingConfirmation = "Exported"
                shareItem = ShareItem(url: url)
            } catch {
                actionError = error.localizedDescription
            }
        }
    }

    private func exportLabel(_ model: EntryContentModel) -> String {
        switch SourceUri.scheme(model.entityKey) {
        case ContactsSource.scheme: "Export as vCard"
        case CalendarSource.scheme: "Export as ICS"
        default: "Export"
        }
    }

    private var errorBinding: Binding<Bool> {
        Binding(get: { actionError != nil }, set: { if !$0 { actionError = nil } })
    }

    private func contentInfoSection(content: any EntityContentMetadata) -> some View {
        Section(entry.metadata.filesystem != nil ? "File" : "Library") {
            LabeledContent("Size", value: StatusFormatters.bytes(content.size))
            LabeledContent("Checksum", value: content.checksum.map { String(format: "%02x", $0) }.joined())
                .textSelection(.enabled)
            if entry.kind == .content {
                LabeledContent("Crates", value: "\(content.crates.count)")
                LabeledContent("Compression", value: content.compression)
            }
        }
    }

    private var kindLabel: String {
        EntryDisplay.kindLabel(path: entry.path, metadata: entry.metadata)
    }

    private var stateLabel: String {
        switch entry.state {
        case .new: "New"
        case .updated: "Updated"
        case .existing: "Existing"
        }
    }
}

#if DEBUG
private struct PreviewHarness: View {
    let entry: DatasetEntry
    let state: EntryDetailViewState
    @State private var filters: EntryMetadataFilters = .default

    var body: some View {
        NavigationStack {
            EntryDetailContent(
                entry: entry,
                state: state,
                session: nil,
                filters: $filters,
                onRefresh: {},
                onClearError: {}
            )
        }
    }
}

#Preview("loading") {
    PreviewHarness(entry: MockServerApiEndpointClient.defaultEntry, state: .initial)
}

#Preview("populated") {
    PreviewHarness(
        entry: MockServerApiEndpointClient.defaultEntry,
        state: EntryDetailViewState(
            metadata: MockServerApiEndpointClient.defaultMetadata,
            isLoading: false
        )
    )
}

#Preview("metadata unavailable") {
    PreviewHarness(
        entry: MockServerApiEndpointClient.extraEntry,
        state: EntryDetailViewState(metadata: nil, isLoading: false)
    )
}

#Preview("with error") {
    PreviewHarness(
        entry: MockServerApiEndpointClient.defaultEntry,
        state: EntryDetailViewState(metadata: nil, isLoading: false, error: "Network error")
    )
}
#endif

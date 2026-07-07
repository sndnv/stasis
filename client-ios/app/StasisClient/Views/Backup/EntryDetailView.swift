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
    var updatesOnly: Bool = true
    var filesOnly: Bool = true
    var noHidden: Bool = true
    var pathQuery: String = ""
    var exactPath: Bool = false

    static let `default` = EntryMetadataFilters()

    func apply(to entries: [PathEntry]) -> [PathEntry] {
        let query = pathQuery.trimmingCharacters(in: .whitespaces).lowercased()
        return entries.filter { entry in
            if updatesOnly, case .existing = entry.state { return false }
            if filesOnly, case .directory = entry.metadata { return false }
            if noHidden, EntryMetadataFilters.isPathHidden(entry: entry) { return false }
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
        .navigationBarTitleDisplayMode(.inline)
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
            LabeledContent("Id", value: StatusFormatters.shortId(entry.id))
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
        let filteredContent = filters.apply(to: buckets.content)
        let filteredMeta = filters.apply(to: buckets.metadata)
        let shownCount = filteredContent.count + filteredMeta.count

        filtersSection(shown: shownCount, total: buckets.totalCount)
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
        HStack(alignment: .top, spacing: 8) {
            stateIcon
            VStack(alignment: .leading, spacing: 2) {
                Text(entityName).font(.callout).bold()
                if !parentPath.isEmpty {
                    Text(parentPath)
                        .font(.caption2.monospaced())
                        .foregroundStyle(.tertiary)
                        .lineLimit(1)
                        .truncationMode(.middle)
                }
                HStack(spacing: 8) {
                    Text(kindLabel)
                    if case .file(let file) = entry.metadata {
                        Text(StatusFormatters.bytes(file.size))
                    }
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

    private var stateIcon: some View {
        let (system, color): (String, Color) = switch entry.state {
        case .new: ("plus.circle.fill", .green)
        case .updated: ("pencil.circle.fill", .orange)
        case .existing: ("checkmark.circle.fill", .secondary)
        }
        return Image(systemName: system).foregroundStyle(color).font(.title3)
    }

    private var entityName: String {
        let trimmed = entry.path.hasSuffix("/") && entry.path.count > 1
            ? String(entry.path.dropLast())
            : entry.path
        let component = (trimmed as NSString).lastPathComponent
        return component.isEmpty ? entry.path : component
    }

    private var parentPath: String {
        let parent = (entry.path as NSString).deletingLastPathComponent
        return parent == "/" ? "" : parent
    }

    private var kindLabel: String {
        switch entry.metadata {
        case .file: "File"
        case .directory: "Directory"
        case .library: "Library"
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

    @MainActor
    init(entry: PathEntry, session: AuthenticatedSession?) {
        self.entry = entry
        self.session = session
        let contentModel: EntryContentModel? = {
            guard let session, entry.metadata.content != nil else { return nil }
            return EntryContentModel.live(
                session: session,
                entityKey: entry.path,
                displayName: Self.entityDisplayName(entry),
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
                if case .file(let file) = entry.metadata {
                    fileSection(file: file)
                }
                if let model {
                    contentSection(model: model)
                }
            }
            .navigationTitle((entry.path as NSString).lastPathComponent)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .sheet(item: $shareItem) { item in
                ShareSheet(url: item.url)
            }
            .alert("Error", isPresented: errorBinding) {
                Button("OK") { actionError = nil }
            } message: {
                Text(actionError ?? "")
            }
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
                shareItem = ShareItem(url: try ExportFile.write(
                    name: model.displayName,
                    fileExtension: ExportFile.inferExtension(bytes),
                    bytes: bytes
                ))
            } catch {
                actionError = error.localizedDescription
            }
        }
    }

    private func export(_ model: EntryContentModel) {
        Task { @MainActor in
            do {
                let content = try await model.exportedContent()
                shareItem = ShareItem(url: try ExportFile.write(
                    name: model.displayName,
                    fileExtension: content.fileExtension,
                    bytes: content.bytes
                ))
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

    private static func entityDisplayName(_ entry: PathEntry) -> String {
        if case .library(let library) = entry.metadata,
           let attributes = try? JSONDecoder().decode([String: String].self, from: library.attributes),
           let name = attributes["name"], !name.isEmpty {
            return name
        }
        let component = (entry.path as NSString).lastPathComponent
        return component.isEmpty ? entry.path : component
    }

    private func fileSection(file: EntityMetadata.File) -> some View {
        Section("File") {
            LabeledContent("Size", value: StatusFormatters.bytes(file.size))
            LabeledContent("Checksum", value: file.checksum.map { String(format: "%02x", $0) }.joined())
                .textSelection(.enabled)
            if entry.kind == .content {
                LabeledContent("Crates", value: "\(file.crates.count)")
                LabeledContent("Compression", value: file.compression)
            }
        }
    }

    private var kindLabel: String {
        switch entry.metadata {
        case .file: "File"
        case .directory: "Directory"
        case .library: "Library"
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

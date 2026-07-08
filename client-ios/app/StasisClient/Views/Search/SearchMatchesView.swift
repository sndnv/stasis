import StasisClientLib
import SwiftUI

struct SearchMatchesView: View {
    @Environment(AppContainer.self) private var container
    let result: DatasetDefinitionResult

    @State private var loadingPath: String?
    @State private var pushTarget: PushTarget?
    @State private var error: String?

    var body: some View {
        List {
            Section("Definition") {
                LabeledContent("Info", value: result.definitionInfo)
                LabeledContent("Entry", value: StatusFormatters.shortId(result.entryId))
                LabeledContent(
                    "Created",
                    value: result.entryCreated.formatted(date: .abbreviated, time: .shortened)
                )
                LabeledContent("Matches", value: "\(result.matches.count)")
            }
            Section {
                ForEach(visibleMatches, id: \.path) { match in
                    Button {
                        Task { await openMatch(match.path) }
                    } label: {
                        MatchRow(
                            path: match.path,
                            state: match.state,
                            isLoading: loadingPath == match.path
                        )
                    }
                    .buttonStyle(.plain)
                    .disabled(loadingPath != nil)
                }
            } header: {
                Text("Matches")
            } footer: {
                if result.matches.count > Self.maxAllowedMatches {
                    Label(
                        "Too many matches (\(result.matches.count)). Showing only the first \(Self.maxAllowedMatches).",
                        systemImage: "exclamationmark.triangle.fill"
                    )
                    .foregroundStyle(.orange)
                }
            }
        }
        .navigationTitle("Matches")
        .navigationSubtitle(result.definitionInfo)
        .navigationBarTitleDisplayMode(.inline)
        .navigationDestination(item: $pushTarget) { target in
            EntryDetailView(entry: target.entry, initialFilters: filters(for: target.path))
        }
        .alert("Error", isPresented: errorBinding) {
            Button("OK") { error = nil }
        } message: {
            Text(error ?? "")
        }
    }

    private var visibleMatches: [Match] {
        Array(
            result.matches
                .map { Match(path: $0.key, state: $0.value) }
                .sorted { $0.path < $1.path }
                .prefix(Self.maxAllowedMatches)
        )
    }

    private static let maxAllowedMatches: Int = 100

    private func openMatch(_ path: String) async {
        guard loadingPath == nil, let session = container.session else { return }
        loadingPath = path
        do {
            let entry = try await session.serverApiClient.datasetEntry(entry: result.entryId)
            pushTarget = PushTarget(path: path, entry: entry)
        } catch {
            self.error = error.localizedDescription
        }
        loadingPath = nil
    }

    private func filters(for path: String) -> EntryMetadataFilters {
        EntryMetadataFilters(
            updatesOnly: false,
            filesOnly: false,
            noHidden: false,
            pathQuery: path,
            exactPath: true,
            kind: .all
        )
    }

    private var errorBinding: Binding<Bool> {
        Binding(
            get: { error != nil },
            set: { if !$0 { error = nil } }
        )
    }

    private struct Match: Equatable {
        let path: String
        let state: FilesystemMetadata.EntityState
    }

    private struct PushTarget: Identifiable, Hashable {
        let path: String
        let entry: DatasetEntry
        var id: String { "\(entry.id.uuidString)/\(path)" }
    }
}

private struct MatchRow: View {
    let path: String
    let state: FilesystemMetadata.EntityState
    let isLoading: Bool

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text(path)
                    .font(.caption.monospaced())
                    .lineLimit(3)
                    .truncationMode(.middle)
                stateBadge
            }
            Spacer()
            if isLoading {
                ProgressView()
            } else {
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 2)
        .contentShape(Rectangle())
    }

    @ViewBuilder
    private var stateBadge: some View {
        let (label, color) = badgeAttributes
        Text(label)
            .font(.caption2.weight(.semibold))
            .padding(.horizontal, 6).padding(.vertical, 2)
            .background(color.opacity(0.15))
            .foregroundStyle(color)
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }

    private var badgeAttributes: (String, Color) {
        switch state {
        case .new: ("NEW", .green)
        case .updated: ("UPDATED", .orange)
        case .existing: ("EXISTING", .blue)
        }
    }
}

#if DEBUG
#Preview("matches") {
    NavigationStack {
        SearchMatchesView(result: DatasetDefinitionResult(
            definitionInfo: "Photos",
            entryId: UUID(),
            entryCreated: .now.addingTimeInterval(-3600),
            matches: [
                "/photos/2026/sunset.jpg": .new,
                "/photos/2026/beach.jpg": .existing(entry: UUID()),
                "/photos/2025/portrait.jpg": .updated
            ]
        ))
    }
    .environment(AppContainer())
}
#endif

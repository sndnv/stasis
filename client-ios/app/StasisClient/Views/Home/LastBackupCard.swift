import StasisClientLib
import SwiftUI

struct LastBackupCard: View {
    let entry: DatasetEntry?
    let isLoading: Bool

    var body: some View {
        GroupBox {
            if isLoading {
                HStack { Spacer(); ProgressView(); Spacer() }
                    .padding(.vertical, 8)
            } else if let entry {
                content(for: entry)
            } else {
                Text("No backups yet")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 4)
            }
        } label: {
            Label("Last Backup", systemImage: "clock.arrow.circlepath")
        }
    }

    @ViewBuilder
    private func content(for entry: DatasetEntry) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            row(label: "Created", value: entry.created.formatted(date: .abbreviated, time: .shortened))
            row(label: "Files", value: "\(entry.data.count)")
            row(label: "Changes", value: entry.changes.map { "\($0)" } ?? "-")
            row(label: "Size", value: ByteCountFormatter.string(
                fromByteCount: entry.size ?? 0, countStyle: .file
            ))
        }
    }

    private func row(label: String, value: String) -> some View {
        HStack {
            Text(label).foregroundStyle(.secondary)
            Spacer()
            Text(value)
        }
        .font(.callout)
    }
}

#Preview("populated") {
    LastBackupCard(
        entry: DatasetEntry(
            id: UUID(),
            definition: UUID(),
            device: UUID(),
            data: [UUID(), UUID()],
            metadata: UUID(),
            changes: 1,
            size: 1024 * 1024 * 42,
            created: Date()
        ),
        isLoading: false
    )
    .padding()
}

#Preview("loading") {
    LastBackupCard(entry: nil, isLoading: true)
        .padding()
}

#Preview("empty") {
    LastBackupCard(entry: nil, isLoading: false)
        .padding()
}

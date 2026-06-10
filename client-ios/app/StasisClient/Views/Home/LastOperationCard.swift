import StasisClientLib
import SwiftUI

struct LastOperationCard: View {
    let operation: HomeModel.LastOperation?
    let isLoading: Bool

    var body: some View {
        GroupBox {
            if isLoading {
                HStack { Spacer(); ProgressView(); Spacer() }
                    .padding(.vertical, 8)
            } else if let operation {
                content(for: operation)
            } else {
                Text("No operations yet")
                    .foregroundStyle(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, 4)
            }
        } label: {
            Label("Last Operation", systemImage: "list.bullet.rectangle")
        }
    }

    @ViewBuilder
    private func content(for operation: HomeModel.LastOperation) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            row(label: "Type", value: label(for: operation.type))
            row(label: "Id", value: shortId(operation.id))
            row(label: "Processed", value: "\(operation.progress.processed) / \(operation.progress.total)")
            if operation.progress.failures > 0 {
                row(label: "Failures", value: "\(operation.progress.failures)")
            }
            row(label: "Completed", value: operation.completed.formatted(.relative(presentation: .named)))
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

    private func label(for type: OperationType) -> String {
        switch type {
        case .backup: "Backup"
        case .recovery: "Recovery"
        case .expiration: "Expiration"
        case .validation: "Validation"
        case .keyRotation: "Key rotation"
        case .garbageCollection: "Garbage collection"
        }
    }

    private func shortId(_ id: OperationId) -> String {
        String(id.uuidString.lowercased().prefix(8))
    }
}

#Preview("populated") {
    LastOperationCard(
        operation: .init(
            id: UUID(),
            type: .backup,
            progress: OperationProgress(
                started: .now.addingTimeInterval(-600),
                total: 250, processed: 248, failures: 1, completed: .now.addingTimeInterval(-60)
            ),
            completed: .now.addingTimeInterval(-60)
        ),
        isLoading: false
    )
    .padding()
}

#Preview("empty") {
    LastOperationCard(operation: nil, isLoading: false)
        .padding()
}

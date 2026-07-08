import Foundation
import SwiftUI

struct IdLabeledContent: View {
    private let label: LocalizedStringKey
    private let value: String

    @State private var copied = false

    init(_ label: LocalizedStringKey, id: UUID) {
        self.label = label
        self.value = id.uuidString.lowercased()
    }

    init(_ label: LocalizedStringKey, value: String) {
        self.label = label
        self.value = value
    }

    var body: some View {
        Button(action: copy) {
            LabeledContent {
                if copied {
                    Label("Copied", systemImage: "checkmark")
                        .labelStyle(.titleAndIcon)
                        .foregroundStyle(.secondary)
                } else {
                    Text(shortened)
                        .font(.callout.monospaced())
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            } label: {
                Text(label)
            }
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityHint("Double tap to copy")
    }

    private var shortened: String {
        value.count > 16 ? "\(value.prefix(8))…\(value.suffix(8))" : value
    }

    private func copy() {
        UIPasteboard.general.string = value
        withAnimation { copied = true }
        Task {
            try? await Task.sleep(for: .seconds(1.5))
            withAnimation { copied = false }
        }
    }
}

#if DEBUG
#Preview {
    Form {
        Section("Metadata") {
            IdLabeledContent("Id", id: UUID())
            IdLabeledContent("Node", value: "node:test")
        }
    }
}
#endif

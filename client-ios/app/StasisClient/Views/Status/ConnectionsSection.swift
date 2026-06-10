import StasisClientLib
import SwiftUI

struct ConnectionsSection: View {
    let servers: [String: ServerState]

    var body: some View {
        Section("Connections") {
            if servers.isEmpty {
                Text("No connection activity yet")
                    .foregroundStyle(.secondary)
            } else {
                ForEach(sortedServers, id: \.key) { entry in
                    row(server: entry.key, state: entry.value)
                }
            }
        }
    }

    private var sortedServers: [(key: String, value: ServerState)] {
        servers.sorted { $0.key < $1.key }
    }

    private func row(server: String, state: ServerState) -> some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                Text(server).font(.callout)
                Text("checked \(state.timestamp.formatted(.relative(presentation: .named)))")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            Label(
                state.reachable ? "Reachable" : "Unreachable",
                systemImage: state.reachable ? "checkmark.circle.fill" : "xmark.circle.fill"
            )
            .foregroundStyle(state.reachable ? .green : .red)
            .labelStyle(.titleAndIcon)
            .font(.caption.weight(.medium))
        }
    }
}

#Preview("populated") {
    Form {
        ConnectionsSection(servers: [
            "https://api.stasis.example": ServerState(reachable: true, timestamp: .now.addingTimeInterval(-30)),
            "https://core.stasis.example": ServerState(reachable: false, timestamp: .now.addingTimeInterval(-600))
        ])
    }
}

#Preview("empty") {
    Form { ConnectionsSection(servers: [:]) }
}

import StasisClientLib
import SwiftUI

struct AvailableCommandsSheet: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.dismiss) private var dismiss
    @State private var model: AvailableCommandsModel?
    @State private var detail: DetailTarget?

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("Available Commands")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Close") { dismiss() }
                    }
                }
                .task { await loadIfNeeded() }
                .sheet(item: $detail) { target in
                    CommandParametersSheet(command: target.command)
                }
        }
    }

    @ViewBuilder
    private var content: some View {
        if let model {
            switch model.state {
            case .loading:
                ProgressView("Loading…").frame(maxWidth: .infinity, maxHeight: .infinity)
            case .loaded(let commands, let lastProcessed) where commands.isEmpty:
                ContentUnavailableView(
                    "No Commands",
                    systemImage: "tray",
                    description: Text(
                        lastProcessed == 0
                            ? "No commands have been received yet."
                            : "All commands up to #\(lastProcessed) have been processed."
                    )
                )
            case .loaded(let commands, let lastProcessed):
                List {
                    ForEach(commands, id: \.sequenceId) { command in
                        Button {
                            detail = DetailTarget(command: command)
                        } label: {
                            CommandRow(command: command, lastProcessed: lastProcessed)
                        }
                        .buttonStyle(.plain)
                    }
                }
            case .failed(let message):
                ContentUnavailableView(
                    "Failed to load",
                    systemImage: "exclamationmark.triangle",
                    description: Text(message)
                )
            }
        } else {
            ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    private func loadIfNeeded() async {
        if model == nil {
            guard let session = container.session else { return }
            model = AvailableCommandsModel(
                session: session,
                preferences: container.configRepository.preferencesStore
            )
        }
        await model?.load()
    }
}

private struct CommandRow: View {
    let command: CommandAsJson
    let lastProcessed: Int64

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(alignment: .firstTextBaseline) {
                Text("#\(command.sequenceId) \(command.commandName)")
                    .font(.headline)
                Spacer()
                if command.sequenceId > lastProcessed {
                    Text("Pending")
                        .font(.caption.weight(.semibold))
                        .padding(.horizontal, 6).padding(.vertical, 2)
                        .background(Color.orange.opacity(0.15))
                        .foregroundStyle(Color.orange)
                        .clipShape(RoundedRectangle(cornerRadius: 4))
                }
            }
            Text(command.created.formatted(date: .abbreviated, time: .shortened))
                .font(.caption).foregroundStyle(.secondary)
            HStack(spacing: 12) {
                Label(command.sourceLabel, systemImage: "person.crop.circle")
                Label(command.targetLabel, systemImage: "iphone")
            }
            .font(.caption2).foregroundStyle(.secondary).labelStyle(.titleAndIcon)
        }
        .padding(.vertical, 2)
        .contentShape(Rectangle())
    }
}

private struct CommandParametersSheet: View {
    let command: CommandAsJson
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Command") {
                    LabeledContent("Sequence", value: "#\(command.sequenceId)")
                    LabeledContent("Name", value: command.commandName)
                }
                Section("Parameters") {
                    Text(command.parametersDescription)
                        .font(.caption.monospaced())
                        .textSelection(.enabled)
                }
            }
            .navigationTitle("Parameters")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

private struct DetailTarget: Identifiable {
    let command: CommandAsJson
    var id: Int64 { command.sequenceId }
}

fileprivate extension CommandAsJson {
    var commandName: String {
        parameters.logoutUser != nil ? "logout_user" : "unrecognized"
    }

    var parametersDescription: String {
        if let logout = parameters.logoutUser {
            return "logout_user(reason: \(logout.reason ?? "—"))"
        }
        return "(empty)"
    }

    var sourceLabel: String {
        switch source {
        case "user": "User"
        case "service": "Service"
        default: source.isEmpty ? "Unknown" : source
        }
    }

    var targetLabel: String {
        target != nil ? "This device" : "Any device"
    }
}

#if DEBUG
#Preview {
    AvailableCommandsSheet()
        .environment(AppContainer())
}
#endif

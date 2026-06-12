import SwiftUI

struct SupportedCommandsSheet: View {
    @Environment(\.dismiss) private var dismiss

    private static let commands: [Command] = [
        Command(
            name: "logout_user",
            description: "Logs out the current user. The session is cleared and the user must sign in again."
        ),
        Command(
            name: "unrecognized",
            description: "Returned for any command the client does not know how to handle yet."
        )
    ]

    var body: some View {
        NavigationStack {
            List {
                ForEach(Array(Self.commands.enumerated()), id: \.element.name) { index, command in
                    VStack(alignment: .leading, spacing: 4) {
                        Text("\(index + 1). \(command.name)")
                            .font(.headline)
                        Text(command.description)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 2)
                }
            }
            .navigationTitle("Supported Commands")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }

    private struct Command: Equatable {
        let name: String
        let description: String
    }
}

#if DEBUG
#Preview {
    SupportedCommandsSheet()
}
#endif

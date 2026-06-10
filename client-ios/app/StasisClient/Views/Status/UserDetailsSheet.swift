import StasisClientLib
import SwiftUI

struct UserDetailsSheet: View {
    let user: User?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                if let user {
                    Section("Metadata") {
                        LabeledContent("Id", value: user.id.uuidString)
                            .textSelection(.enabled)
                        LabeledContent("Created", value: user.created.formatted(date: .abbreviated, time: .shortened))
                        LabeledContent("Updated", value: user.updated.formatted(date: .abbreviated, time: .shortened))
                    }
                    if !user.permissions.isEmpty {
                        Section("Permissions") {
                            ForEach(user.permissions.sorted(), id: \.self) { permission in
                                Text(permission).font(.callout)
                            }
                        }
                    }
                } else {
                    Section {
                        Text("No user available")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("User Details")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
}

#Preview("with permissions") {
    UserDetailsSheet(user: User(
        id: UUID(),
        salt: "salt",
        active: true,
        limits: nil,
        permissions: ["A1", "A2", "A3"],
        created: Date().addingTimeInterval(-86_400 * 30),
        updated: Date()
    ))
}

#Preview("no permissions") {
    UserDetailsSheet(user: User(
        id: UUID(),
        salt: "salt",
        active: true,
        limits: nil,
        permissions: [],
        created: Date().addingTimeInterval(-86_400 * 30),
        updated: Date()
    ))
}

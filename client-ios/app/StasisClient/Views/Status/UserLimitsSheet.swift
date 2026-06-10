import StasisClientLib
import SwiftUI

struct UserLimitsSheet: View {
    let user: User?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                if let limits = user?.limits {
                    Section("Limits") {
                        LabeledContent("Max Devices", value: "\(limits.maxDevices)")
                        LabeledContent("Max Crates", value: "\(limits.maxCrates)")
                        LabeledContent("Max Storage", value: StatusFormatters.bytes(limits.maxStorage))
                        LabeledContent("Max Storage / Crate", value: StatusFormatters.bytes(limits.maxStoragePerCrate))
                        LabeledContent("Min Retention", value: StatusFormatters.duration(limits.minRetention))
                        LabeledContent("Max Retention", value: StatusFormatters.duration(limits.maxRetention))
                    }
                } else {
                    Section {
                        Text("No limits set for user")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("User Limits")
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

#Preview("with limits") {
    UserLimitsSheet(user: User(
        id: UUID(),
        salt: "salt",
        active: true,
        limits: User.Limits(
            maxDevices: 5,
            maxCrates: 200,
            maxStorage: 1024 * 1024 * 1024 * 50,
            maxStoragePerCrate: 1024 * 1024 * 128,
            maxRetention: SecondsDuration(86_400 * 30),
            minRetention: SecondsDuration(86_400 * 7)
        ),
        permissions: [],
        created: Date().addingTimeInterval(-86_400 * 30),
        updated: Date()
    ))
}

#Preview("no limits") {
    UserLimitsSheet(user: User(
        id: UUID(),
        salt: "salt",
        active: true,
        limits: nil,
        permissions: [],
        created: Date().addingTimeInterval(-86_400 * 30),
        updated: Date()
    ))
}

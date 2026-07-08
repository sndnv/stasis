import StasisClientLib
import SwiftUI

struct DeviceDetailsSheet: View {
    let device: Device?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                if let device {
                    Section("Metadata") {
                        IdLabeledContent("Id", id: device.id)
                        LabeledContent("Created", value: device.created.formatted(date: .abbreviated, time: .shortened))
                        LabeledContent("Updated", value: device.updated.formatted(date: .abbreviated, time: .shortened))
                    }
                } else {
                    Section {
                        Text("No device available")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Device Details")
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

#Preview {
    DeviceDetailsSheet(device: Device(
        id: UUID(),
        name: "My iPhone",
        node: UUID(),
        owner: UUID(),
        active: true,
        limits: nil,
        created: Date().addingTimeInterval(-86_400 * 60),
        updated: Date()
    ))
}

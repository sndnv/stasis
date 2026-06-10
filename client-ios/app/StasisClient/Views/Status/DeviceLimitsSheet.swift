import StasisClientLib
import SwiftUI

struct DeviceLimitsSheet: View {
    let device: Device?
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                if let limits = device?.limits {
                    Section("Limits") {
                        LabeledContent("Max Crates", value: "\(limits.maxCrates)")
                        LabeledContent("Max Storage", value: StatusFormatters.bytes(limits.maxStorage))
                        LabeledContent("Max Storage / Crate", value: StatusFormatters.bytes(limits.maxStoragePerCrate))
                        LabeledContent("Min Retention", value: StatusFormatters.duration(limits.minRetention))
                        LabeledContent("Max Retention", value: StatusFormatters.duration(limits.maxRetention))
                    }
                } else {
                    Section {
                        Text("No limits set for device")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .navigationTitle("Device Limits")
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
    DeviceLimitsSheet(device: Device(
        id: UUID(),
        name: "My iPhone",
        node: UUID(),
        owner: UUID(),
        active: true,
        limits: Device.Limits(
            maxCrates: 100,
            maxStorage: 1024 * 1024 * 1024 * 10,
            maxStoragePerCrate: 1024 * 1024 * 64,
            maxRetention: SecondsDuration(86_400 * 14),
            minRetention: SecondsDuration(86_400 * 3)
        ),
        created: Date().addingTimeInterval(-86_400 * 60),
        updated: Date()
    ))
}

#Preview("no limits") {
    DeviceLimitsSheet(device: Device(
        id: UUID(),
        name: "Test Device",
        node: UUID(),
        owner: UUID(),
        active: false,
        limits: nil,
        created: Date().addingTimeInterval(-86_400 * 60),
        updated: Date()
    ))
}

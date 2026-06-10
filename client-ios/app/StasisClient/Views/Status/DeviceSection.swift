import StasisClientLib
import SwiftUI

struct DeviceSection: View {
    let device: Device?
    let isLoading: Bool
    @State private var showDetails: Bool = false
    @State private var showLimits: Bool = false

    var body: some View {
        Section("Device") {
            if isLoading {
                ProgressView().frame(maxWidth: .infinity)
            } else if let device {
                idRow(device: device)
                    .sheet(isPresented: $showDetails) {
                        DeviceDetailsSheet(device: device)
                    }
                LabeledContent("Name", value: device.name)
                LabeledContent("Status", value: device.active ? "Active" : "Inactive")
                maxStorageRow(limits: device.limits)
                    .sheet(isPresented: $showLimits) {
                        DeviceLimitsSheet(device: device)
                    }
            } else {
                Text("Unavailable").foregroundStyle(.secondary)
            }
        }
    }

    private func idRow(device: Device) -> some View {
        Button {
            showDetails = true
        } label: {
            HStack {
                Text("Id")
                Spacer()
                Text(StatusFormatters.shortId(device.id))
                    .foregroundStyle(.secondary)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .contentShape(.rect)
        }
        .foregroundStyle(.primary)
    }

    private func maxStorageRow(limits: Device.Limits?) -> some View {
        Button {
            showLimits = true
        } label: {
            HStack {
                Text("Max Storage")
                Spacer()
                Text(limits.map { StatusFormatters.bytes($0.maxStorage) } ?? "Unlimited")
                    .foregroundStyle(.secondary)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .contentShape(.rect)
        }
        .foregroundStyle(.primary)
    }
}

#Preview("with limits") {
    Form {
        DeviceSection(
            device: Device(
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
                created: .now,
                updated: .now
            ),
            isLoading: false
        )
    }
}

#Preview("no limits") {
    Form {
        DeviceSection(
            device: Device(
                id: UUID(),
                name: "Test Device",
                node: UUID(),
                owner: UUID(),
                active: false,
                limits: nil,
                created: .now,
                updated: .now
            ),
            isLoading: false
        )
    }
}

#Preview("loading") {
    Form { DeviceSection(device: nil, isLoading: true) }
}

#Preview("unavailable") {
    Form { DeviceSection(device: nil, isLoading: false) }
}

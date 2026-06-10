import StasisClientLib
import SwiftUI

struct UserSection: View {
    let user: User?
    let isLoading: Bool
    @State private var showDetails: Bool = false
    @State private var showLimits: Bool = false

    var body: some View {
        Section("User") {
            if isLoading {
                ProgressView().frame(maxWidth: .infinity)
            } else if let user {
                idRow(user: user)
                    .sheet(isPresented: $showDetails) {
                        UserDetailsSheet(user: user)
                    }
                LabeledContent("Status", value: user.active ? "Active" : "Inactive")
                maxStorageRow(limits: user.limits)
                    .sheet(isPresented: $showLimits) {
                        UserLimitsSheet(user: user)
                    }
            } else {
                Text("Unavailable").foregroundStyle(.secondary)
            }
        }
    }

    private func idRow(user: User) -> some View {
        Button {
            showDetails = true
        } label: {
            HStack {
                Text("Id")
                Spacer()
                Text(StatusFormatters.shortId(user.id))
                    .foregroundStyle(.secondary)
                Image(systemName: "chevron.right")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.tertiary)
            }
            .contentShape(.rect)
        }
        .foregroundStyle(.primary)
    }

    private func maxStorageRow(limits: User.Limits?) -> some View {
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
        UserSection(
            user: User(
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
                created: .now,
                updated: .now
            ),
            isLoading: false
        )
    }
}

#Preview("no limits") {
    Form {
        UserSection(
            user: User(
                id: UUID(),
                salt: "salt",
                active: false,
                limits: nil,
                permissions: [],
                created: .now,
                updated: .now
            ),
            isLoading: false
        )
    }
}

#Preview("loading") {
    Form { UserSection(user: nil, isLoading: true) }
}

#Preview("unavailable") {
    Form { UserSection(user: nil, isLoading: false) }
}

import SwiftUI

struct UserCredentialsSection: View {
    @Environment(AppContainer.self) private var container
    @State private var activeSheet: ActiveSheet?

    private enum ActiveSheet: Identifiable {
        case updatePassword
        case updateSalt

        var id: Self { self }
    }

    var body: some View {
        Section("User Credentials") {
            Button {
                activeSheet = .updatePassword
            } label: {
                Label("Update Password", systemImage: "key.fill")
            }
            .sheet(item: $activeSheet) { sheet in
                switch sheet {
                case .updatePassword:
                    UpdatePasswordSheet { current, new in
                        try await container.updateUserPassword(currentPassword: current, newPassword: new)
                    }
                case .updateSalt:
                    UpdateSaltSheet { current, salt in
                        try await container.updateUserSalt(currentPassword: current, newSalt: salt)
                    }
                }
            }
            Button {
                activeSheet = .updateSalt
            } label: {
                Label("Update Salt", systemImage: "shuffle")
            }
        }
    }
}

#Preview {
    Form { UserCredentialsSection() }
        .environment(AppContainer())
        .environment(ToastCenter(displayDuration: .seconds(2.5)))
}

import SwiftUI

struct UserCredentialsSection: View {
    @Environment(AppContainer.self) private var container
    @State private var showUpdatePassword: Bool = false
    @State private var showUpdateSalt: Bool = false

    var body: some View {
        Section("User Credentials") {
            Button {
                showUpdatePassword = true
            } label: {
                Label("Update Password", systemImage: "key.fill")
            }
            Button {
                showUpdateSalt = true
            } label: {
                Label("Update Salt", systemImage: "shuffle")
            }
        }
        .sheet(isPresented: $showUpdatePassword) {
            UpdatePasswordSheet { current, new in
                try await container.updateUserPassword(currentPassword: current, newPassword: new)
            }
        }
        .sheet(isPresented: $showUpdateSalt) {
            UpdateSaltSheet { current, salt in
                try await container.updateUserSalt(currentPassword: current, newSalt: salt)
            }
        }
    }
}

#Preview {
    Form { UserCredentialsSection() }
        .environment(AppContainer())
}

import SwiftUI

struct UserCredentialsSection: View {
    var body: some View {
        Section("User Credentials") {
            PlaceholderActionRow(title: "Update Password")
            PlaceholderActionRow(title: "Update Salt")
        }
    }
}

#Preview {
    Form { UserCredentialsSection() }
}

import SwiftUI

struct BootstrapProvideUsernameView: View {
    @Binding var path: [BootstrapStep]
    @Bindable var state: BootstrapState

    var body: some View {
        Form {
            Section {
                BootstrapLogo()
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }

            Section {
                HStack(spacing: 8) {
                    BootstrapInfoButton(
                        title: "User",
                        message: "User name (for connection to the server, when pulling secrets)."
                    )
                    TextField("user", text: $state.username)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
            } header: {
                Text("User")
            }
        }
        .navigationTitle("User")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            BootstrapStepRow(
                path: $path,
                step: "2/5",
                nextDisabled: state.username.isEmpty
            ) {
                path.append(.providePassword)
            }
            .background(.bar)
        }
    }
}

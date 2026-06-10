import SwiftUI

struct BootstrapProvidePasswordView: View {
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
                        title: "User Password",
                        message: "This password is required for encrypting the secrets used by this device. "
                            + "Your password is never stored, sent to the server or shared with other services."
                    )
                    SecureField("Password", text: $state.userPassword)
                }
            } header: {
                Text("User Password")
            }

            Section {
                HStack(spacing: 8) {
                    BootstrapInfoButton(
                        title: "Verify Password",
                        message: "Your password is never stored, sent to the server or shared with other services."
                    )
                    SecureField("Password", text: $state.userPasswordConfirmation)
                }
            } header: {
                Text("Verify Password")
            } footer: {
                if !state.userPasswordConfirmation.isEmpty && !passwordsMatch {
                    Text("Passwords do not match").foregroundStyle(.red)
                }
            }
        }
        .navigationTitle("Password")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            BootstrapStepRow(
                path: $path,
                step: "3/5",
                nextDisabled: state.userPassword.isEmpty || !passwordsMatch
            ) {
                path.append(.provideSecret)
            }
            .background(.bar)
        }
    }

    private var passwordsMatch: Bool {
        state.userPassword == state.userPasswordConfirmation
    }
}

import SwiftUI

struct BootstrapProvideSecretView: View {
    @Binding var path: [BootstrapStep]
    @Bindable var state: BootstrapState

    var body: some View {
        Form {
            Section {
                BootstrapLogo()
                    .listRowBackground(Color.clear)
                    .listRowInsets(EdgeInsets())
            }

            if state.hasExistingDeviceSecret {
                Section {
                    Toggle(isOn: $state.overwriteExisting) {
                        BootstrapSwitchLabel(
                            title: "Overwrite device secret",
                            subtitle: "The secret currently stored on the device will be overwritten."
                                + " Any data encrypted with the current secret may become inaccessible!"
                        )
                    }
                }
            }

            if state.overwriteExisting {
                Section {
                    Toggle(isOn: $state.pullSecret) {
                        BootstrapSwitchLabel(
                            title: "Download device secret",
                            subtitle: "If this device has its secret stored on the server, "
                                + "it will be downloaded during bootstrap."
                        )
                    }
                }
            }

            if state.overwriteExisting && state.pullSecret {
                Section {
                    Toggle(isOn: $state.overrideRemotePassword) {
                        BootstrapSwitchLabel(
                            title: "Override remote password",
                            subtitle: "Provide a different password for decrypting the downloaded secret."
                        )
                    }
                }
            }

            if state.overwriteExisting && state.pullSecret && state.overrideRemotePassword {
                remotePasswordSections
            }
        }
        .navigationTitle("Secret")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            BootstrapStepRow(
                path: $path,
                step: "4/5",
                nextDisabled: !remotePasswordsValid
            ) {
                path.append(.provideCode)
            }
            .background(.bar)
        }
    }

    @ViewBuilder
    private var remotePasswordSections: some View {
        Section {
            HStack(spacing: 8) {
                BootstrapInfoButton(
                    title: "Remote Password",
                    message: "If the device secret stored on the server was encrypted with a different password,"
                        + " it can be provided here. Otherwise, the current user password will be used"
                        + " for the decryption."
                )
                SecureField("Password", text: $state.remotePassword)
            }
        } header: {
            Text("Remote Password")
        }

        Section {
            HStack(spacing: 8) {
                BootstrapInfoButton(
                    title: "Verify Password",
                    message: "Your password is never stored, sent to the server or shared with other services."
                )
                SecureField("Password", text: $state.remotePasswordConfirmation)
            }
        } header: {
            Text("Verify Password")
        } footer: {
            if !state.remotePasswordConfirmation.isEmpty && !remotePasswordsMatch {
                Text("Passwords do not match").foregroundStyle(.red)
            }
        }
    }

    private var remotePasswordsMatch: Bool {
        state.remotePassword == state.remotePasswordConfirmation
    }

    private var remotePasswordsValid: Bool {
        guard state.overwriteExisting, state.pullSecret, state.overrideRemotePassword else {
            return true
        }
        return !state.remotePassword.isEmpty && remotePasswordsMatch
    }
}

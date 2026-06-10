import SwiftUI

struct BootstrapProvideServerView: View {
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
                        icon: "exclamationmark.triangle",
                        title: "Bootstrap Server URL",
                        message: "Make sure you are entering the URL of a trusted server "
                            + "and that it is entered exactly as shown by that server."
                    )
                    Text("https://").foregroundStyle(.secondary)
                    TextField("server.example.com", text: $state.serverHost)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                }
            } header: {
                Text("Bootstrap Server URL")
            }
        }
        .navigationTitle("Server")
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            BootstrapStepRow(
                path: $path,
                step: "1/5",
                nextDisabled: state.serverHost.isEmpty
            ) {
                path.append(.provideUsername)
            }
            .background(.bar)
        }
    }
}

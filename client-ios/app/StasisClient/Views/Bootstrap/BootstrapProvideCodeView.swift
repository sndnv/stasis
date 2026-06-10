import StasisClientLib
import SwiftUI

struct BootstrapProvideCodeView: View {
    @Binding var path: [BootstrapStep]
    @Bindable var state: BootstrapState
    @Environment(AppContainer.self) private var container

    @State private var code: String = ""
    @State private var inProgress: Bool = false
    @State private var error: String?

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
                        title: "Bootstrap Code",
                        message: "This code is required for securely initializing the device "
                            + "and should be entered exactly as shown by the server."
                    )
                    TextField("Code", text: $code)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .disabled(inProgress)
                }
            } header: {
                Text("Bootstrap Code")
            }
        }
        .navigationTitle("Code")
        .navigationBarTitleDisplayMode(.inline)
        .overlay {
            if inProgress {
                ProgressView()
                    .controlSize(.large)
                    .padding(24)
                    .background(.regularMaterial, in: .rect(cornerRadius: 12))
            }
        }
        .alert("Bootstrap Failed", isPresented: alertBinding) {
            Button("OK") { error = nil }
        } message: {
            Text(error ?? "")
        }
        .safeAreaInset(edge: .bottom) {
            BootstrapStepRow(
                path: $path,
                step: "5/5",
                nextLabel: "Finish",
                nextDisabled: code.isEmpty || inProgress
            ) {
                run()
            }
            .background(.bar)
        }
    }

    private var alertBinding: Binding<Bool> {
        Binding(
            get: { error != nil },
            set: { if !$0 { error = nil } }
        )
    }

    private func run() {
        inProgress = true
        let request = state.toRequest(bootstrapCode: code)
        Task {
            do {
                try await container.bootstrap(request: request)
            } catch {
                inProgress = false
                self.error = describeBootstrapError(error)
            }
        }
    }

    private func describeBootstrapError(_ error: any Error) -> String {
        if error is InvalidBootstrapCodeFailure {
            return "An invalid bootstrap code was provided.\n\n"
                + "Verify that the code is entered correctly or obtain a new one."
        }
        if error is AccessDeniedFailure {
            return "Invalid user and/or password provided"
        }
        return "An unexpected failure was encountered during bootstrap: \(error.localizedDescription)"
    }
}

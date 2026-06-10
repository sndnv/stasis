import StasisClientLib
import SwiftUI

struct LoginView: View {
    @Environment(AppContainer.self) private var container

    @State private var username: String = ""
    @State private var password: String = ""
    @State private var rememberUsername: Bool = false
    @State private var inProgress: Bool = false
    @State private var error: String?
    @State private var showMoreOptions: Bool = false

    var body: some View {
        Form {
            Section {
                HStack {
                    Spacer()
                    Image("StasisLogo")
                        .resizable()
                        .scaledToFit()
                        .frame(width: 160, height: 160)
                    Spacer()
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets())
            }

            Section {
                TextField("User", text: $username)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .disabled(inProgress)
            }

            Section {
                SecureField("User Password", text: $password)
                    .disabled(inProgress)
            }

            Section {
                Toggle(isOn: $rememberUsername) {
                    Text("Remember me")
                        .foregroundStyle(.secondary)
                }
                .disabled(inProgress)
            }.listRowBackground(Color.clear)
        }
        .onAppear(perform: prefillSavedUsername)
        .overlay {
            if inProgress {
                ProgressView()
                    .controlSize(.large)
                    .padding(24)
                    .background(.regularMaterial, in: .rect(cornerRadius: 12))
            }
        }
        .alert("Login Failed", isPresented: alertBinding) {
            Button("OK") { error = nil }
        } message: {
            Text(error ?? "")
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: 12) {
                Button {
                    run()
                } label: {
                    Text("Login")
                        .font(.headline)
                        .frame(maxWidth: .infinity, minHeight: 32)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(!canSubmit)
                Button("More options") { showMoreOptions = true }
                    .font(.callout)
                    .disabled(inProgress)
            }
            .padding()
            .background(.bar)
        }
        .sheet(isPresented: $showMoreOptions) {
            LoginMoreOptionsView()
        }
    }

    private var canSubmit: Bool {
        !username.isEmpty && !password.isEmpty && !inProgress
    }

    private var alertBinding: Binding<Bool> {
        Binding(
            get: { error != nil },
            set: { if !$0 { error = nil } }
        )
    }

    private func prefillSavedUsername() {
        guard username.isEmpty else { return }
        if let saved = container.configRepository.preferencesStore.savedUsername() {
            username = saved
            rememberUsername = true
        }
    }

    private func run() {
        inProgress = true
        Task {
            do {
                try await container.login(
                    username: username,
                    password: password,
                    rememberUsername: rememberUsername
                )
            } catch {
                inProgress = false
                self.error = describeLoginError(error)
            }
        }
    }

    private func describeLoginError(_ error: any Error) -> String {
        if error is AccessDeniedFailure {
            return "Invalid user and/or password provided"
        }
        return error.localizedDescription
    }
}

#Preview {
    LoginView()
        .environment(AppContainer())
}

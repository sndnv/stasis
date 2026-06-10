import SwiftUI

struct LoginReInitializeDeviceView: View {
    @Environment(AppContainer.self) private var container
    @Environment(\.dismiss) private var dismiss

    @State private var inProgress: Bool = false
    @State private var error: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 20) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .imageScale(.large)
                    .font(.system(size: 56))
                    .foregroundStyle(.orange)
                Text("Re-initialize device")
                    .font(.title.weight(.semibold))
                Text("Re-running the bootstrap process will replace this device's configuration. "
                    + "Any data encrypted with the current device secret may become inaccessible.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal)
                Spacer()
            }
            .padding()
            .navigationTitle("Re-initialize device")
            .navigationBarTitleDisplayMode(.inline)
            .overlay {
                if inProgress {
                    ProgressView()
                        .controlSize(.large)
                        .padding(24)
                        .background(.regularMaterial, in: .rect(cornerRadius: 12))
                }
            }
            .alert("Re-initialize Failed", isPresented: errorBinding) {
                Button("OK") { error = nil }
            } message: {
                Text(error ?? "")
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }.disabled(inProgress)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Re-initialize", role: .destructive) { run() }
                        .disabled(inProgress)
                }
            }
        }
    }

    private var errorBinding: Binding<Bool> {
        Binding(get: { error != nil }, set: { if !$0 { error = nil } })
    }

    private func run() {
        inProgress = true
        Task {
            do {
                try await container.reinitializeDevice()
            } catch {
                inProgress = false
                self.error = error.localizedDescription
            }
        }
    }
}

#Preview {
    LoginReInitializeDeviceView()
        .environment(AppContainer())
}

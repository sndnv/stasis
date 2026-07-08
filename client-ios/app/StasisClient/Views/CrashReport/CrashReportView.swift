import SwiftUI

struct CrashReportView: View {
    @Environment(AppContainer.self) private var container
    @Environment(ToastCenter.self) private var toasts

    @State private var busy = false

    var body: some View {
        VStack(spacing: 24) {
            Spacer()

            Image(systemName: "exclamationmark.triangle.fill")
                .font(.system(size: 56))
                .foregroundStyle(.orange)
                .accessibilityHidden(true)

            VStack(spacing: 8) {
                Text("stasis keeps stopping unexpectedly")
                    .font(.title2.weight(.semibold))
                    .multilineTextAlignment(.center)
                Text("It failed to start several times in a row. Try one of the options below.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
            }

            DisclosureGroup("More information") {
                ScrollView {
                    Text(container.lastCrashSummary ?? "Unrecoverable error")
                        .font(.footnote.monospaced())
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .textSelection(.enabled)
                }
                .frame(maxHeight: 160)
            }

            Spacer()

            VStack(spacing: 12) {
                Button {
                    container.dismissCrashLoop()
                } label: {
                    Text("Continue anyway").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)

                Button {
                    Task { await sendReport() }
                } label: {
                    Text("Send report now").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button {
                    Task { await forceLogout() }
                } label: {
                    Text("Force logout").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)

                Button(role: .destructive) {
                    Task { await forceReset() }
                } label: {
                    Text("Force reset").frame(maxWidth: .infinity)
                }
                .buttonStyle(.bordered)
            }
            .controlSize(.large)
            .disabled(busy)
        }
        .padding(24)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }

    private func sendReport() async {
        busy = true
        await container.analyticsCollector.send()
        busy = false
        toasts.show("Report sent")
    }

    private func forceLogout() async {
        busy = true
        await container.logout()
        container.dismissCrashLoop()
    }

    private func forceReset() async {
        busy = true
        try? await container.resetConfiguration()
        container.dismissCrashLoop()
    }
}

#Preview {
    CrashReportView()
        .environment(AppContainer())
        .environment(ToastCenter(displayDuration: .seconds(2.5)))
}

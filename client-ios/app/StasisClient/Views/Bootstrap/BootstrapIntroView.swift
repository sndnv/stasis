import SwiftUI

struct BootstrapIntroView: View {
    @Binding var path: [BootstrapStep]

    var body: some View {
        VStack(spacing: 20) {
            BootstrapLogo()
            Text("Device Bootstrap")
                .font(.title.weight(.semibold))
            Text("The application will now guide you through the steps necessary to (re-)initialize this device.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
                .padding(.horizontal)
            Spacer()
            Button("Next") { path.append(.provideServer) }
                .buttonStyle(.borderedProminent)
        }
        .padding()
        .navigationTitle("Bootstrap")
        .navigationBarTitleDisplayMode(.inline)
    }
}

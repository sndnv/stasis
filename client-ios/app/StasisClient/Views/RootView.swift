import SwiftUI

struct RootView: View {
    @Environment(AppContainer.self) private var container

    var body: some View {
        switch container.appStateModel.state {
        case .unconfigured: BootstrapRootView()
        case .configured: LoginView()
        case .restoring: SessionRestoreSplash()
        case .authenticated: MainView()
        }
    }
}

private struct SessionRestoreSplash: View {
    var body: some View {
        VStack(spacing: 16) {
            Image("StasisLogo")
                .resizable()
                .scaledToFit()
                .frame(width: 120, height: 120)
            ProgressView()
                .controlSize(.regular)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(Color(.systemBackground))
    }
}

#Preview {
    RootView()
        .environment(AppContainer())
}

import SwiftUI

struct BootstrapRootView: View {
    @Environment(AppContainer.self) private var container
    @State private var path: [BootstrapStep] = []
    @State private var state: BootstrapState?

    var body: some View {
        let resolved = state ?? BootstrapState(preferences: container.configRepository.preferencesStore)
        NavigationStack(path: $path) {
            BootstrapIntroView(path: $path)
                .navigationDestination(for: BootstrapStep.self) { step in
                    switch step {
                    case .provideServer:
                        BootstrapProvideServerView(path: $path, state: resolved)
                    case .provideUsername:
                        BootstrapProvideUsernameView(path: $path, state: resolved)
                    case .providePassword:
                        BootstrapProvidePasswordView(path: $path, state: resolved)
                    case .provideSecret:
                        BootstrapProvideSecretView(path: $path, state: resolved)
                    case .provideCode:
                        BootstrapProvideCodeView(path: $path, state: resolved)
                    }
                }
        }
        .onAppear { if state == nil { state = resolved } }
    }
}

#Preview {
    BootstrapRootView()
        .environment(AppContainer())
}

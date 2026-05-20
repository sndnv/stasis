import SwiftUI
import FileProvider
import StasisClientLib

@main
struct StasisClientApp: App {
    @State private var container = AppContainer()

    init() {
        Task { await Self.registerFileProviderDomain() }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(container)
        }
    }

    private static func registerFileProviderDomain() async {
        let domain = NSFileProviderDomain(
            identifier: NSFileProviderDomainIdentifier("stasis.client.ios.default"),
            displayName: "stasis"
        )
        do {
            try await NSFileProviderManager.add(domain)
        } catch {
            // Re-registering an existing domain is benign; log and continue.
            print("FileProvider domain registration: \(error)")
        }
    }
}

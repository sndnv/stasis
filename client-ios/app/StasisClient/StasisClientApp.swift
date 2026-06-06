import BackgroundTasks
import FileProvider
import StasisClientLib
import SwiftUI

@main
struct StasisClientApp: App {
    @State private var container = AppContainer()

    init() {
        let scheduler = container.backgroundScheduler
        BGTaskScheduler.shared.register(
            forTaskWithIdentifier: BackgroundScheduler.processingTaskIdentifier,
            using: nil
        ) { task in
            let handlerTask = Task { @Sendable in
                await scheduler.executeReady()
                task.setTaskCompleted(success: !Task.isCancelled)
            }
            task.expirationHandler = { handlerTask.cancel() }
        }
        Task { await Self.registerFileProviderDomain() }
        Task { [scheduler] in await scheduler.start() }
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
            print("FileProvider domain registration: \(error)")
        }
    }
}

import BackgroundTasks
import FileProvider
import OSLog
import StasisClientLib
import SwiftUI

private let appLogger = Logger(subsystem: "stasis.client.ios", category: "StasisClientApp")

@main
struct StasisClientApp: App {
    @State private var container = AppContainer()
    @State private var toasts = ToastCenter(displayDuration: .seconds(2.5))

    private static let healthyStartupDelay: Duration = .seconds(4)

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
        container.startCrashReporting()
    }

    var body: some Scene {
        WindowGroup {
            Group {
                if container.isCrashLooping {
                    CrashReportView()
                } else {
                    RootView()
                        .task { await onAppLaunch() }
                        .task { await markStartupHealthy() }
                }
            }
            .environment(container)
            .toastLayer()
            .environment(toasts)
        }
    }

    private func markStartupHealthy() async {
        try? await Task.sleep(for: Self.healthyStartupDelay)
        guard !Task.isCancelled else { return }
        container.markStartedHealthy()
    }

    private func onAppLaunch() async {
        await withTaskGroup(of: Void.self) { group in
            group.addTask { await Self.registerFileProviderDomain() }
            group.addTask { await container.backgroundScheduler.start() }
            group.addTask { await container.restoreSession() }
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
            appLogger.error("FileProvider domain registration failed: \(error.localizedDescription, privacy: .public)")
        }
    }
}

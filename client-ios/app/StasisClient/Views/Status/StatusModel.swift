import Foundation
import Observation
import StasisClientLib

@MainActor
@Observable
final class StatusModel {
    private let session: AuthenticatedSession
    private let trackers: DefaultTrackers

    private(set) var user: User?
    private(set) var device: Device?
    private(set) var servers: [String: ServerState] = [:]
    private(set) var isLoading: Bool = true
    private(set) var error: String?

    init(session: AuthenticatedSession, trackers: DefaultTrackers) {
        self.session = session
        self.trackers = trackers
    }

    func clearError() {
        error = nil
    }

    func start() async {
        await load()
        await observeServers()
    }

    func refresh() async {
        await load()
    }

    func load() async {
        do {
            async let userFetch = session.serverApiClient.user()
            async let deviceFetch = session.serverApiClient.device()
            user = try await userFetch
            device = try await deviceFetch
        } catch {
            self.error = error.localizedDescription
        }
        servers = await trackers.server.snapshot()
        isLoading = false
    }

    private func observeServers() async {
        for await update in await trackers.server.updates() {
            servers = update
        }
    }
}

import Foundation

public protocol ServiceDiscoveryProvider: Sendable {
    func latest<T: Sendable>(_ type: T.Type) async throws -> T
    func stop() async
}

public actor DisabledServiceDiscoveryProvider: ServiceDiscoveryProvider {
    private let initialClients: [any ServiceApiClient]

    public init(initialClients: [any ServiceApiClient]) {
        self.initialClients = initialClients
    }

    public func latest<T: Sendable>(_ type: T.Type) async throws -> T {
        try ServiceDiscoveryProviders.extractClient(from: initialClients, type: type)
    }

    public func stop() {}
}

public actor DefaultServiceDiscoveryProvider: ServiceDiscoveryProvider {
    private let initialDelay: Duration
    private let interval: Duration
    private let clientFactory: any ServiceApiClientFactory
    private var clients: [String: any ServiceApiClient]
    private var task: Task<Void, Never>?

    static let failureIntervalReduction: Int64 = 10

    public init(
        initialDelay: Duration,
        interval: Duration,
        initialClients: [String: any ServiceApiClient],
        clientFactory: any ServiceApiClientFactory
    ) {
        self.initialDelay = initialDelay
        self.interval = interval
        self.clientFactory = clientFactory
        self.clients = initialClients
        Task { [weak self] in
            await self?.runPollLoop()
        }
    }

    public func latest<T: Sendable>(_ type: T.Type) async throws -> T {
        let matching = clients.values.compactMap { $0 as? T }.shuffled()
        guard let chosen = matching.first else {
            throw DiscoveryFailure(message: "Service client [\(type)] was not found")
        }
        return chosen
    }

    public func stop() {
        task?.cancel()
        task = nil
    }

    private func runPollLoop() async {
        try? await Task.sleep(for: initialDelay)
        while !Task.isCancelled {
            do {
                let discoveryClient = try ServiceDiscoveryProviders.extractClient(
                    from: Array(clients.values),
                    type: (any ServiceDiscoveryClient).self
                )
                let result = try await discoveryClient.latest(isInitialRequest: true)
                apply(result: result)
                try await Task.sleep(for: fuzzy(interval: interval))
            } catch is CancellationError {
                return
            } catch {
                try? await Task.sleep(for: reducedInterval())
            }
        }
    }

    private func apply(result: ServiceDiscoveryResult) {
        switch result {
        case .keepExisting:
            return
        case .switchTo(let endpoints, let recreateExisting):
            if recreateExisting {
                clients.removeAll()
                let core = clientFactory.create(endpoint: endpoints.core)
                clients[endpoints.core.id] = core
                clients[endpoints.api.id] = clientFactory.create(endpoint: endpoints.api, coreClient: core)
                clients[endpoints.discovery.id] = clientFactory.create(endpoint: endpoints.discovery)
            } else {
                let core: any ServiceApiClient = {
                    if let existing = clients[endpoints.core.id] {
                        return existing
                    }
                    let created = clientFactory.create(endpoint: endpoints.core)
                    clients[endpoints.core.id] = created
                    return created
                }()
                if clients[endpoints.api.id] == nil {
                    clients[endpoints.api.id] = clientFactory.create(endpoint: endpoints.api, coreClient: core)
                }
                if clients[endpoints.discovery.id] == nil {
                    clients[endpoints.discovery.id] = clientFactory.create(endpoint: endpoints.discovery)
                }
                let keep: Set<String> = [endpoints.api.id, endpoints.core.id, endpoints.discovery.id]
                for key in clients.keys where !keep.contains(key) {
                    clients.removeValue(forKey: key)
                }
            }
        }
    }

    private func reducedInterval() -> Duration {
        let intervalMs = interval.inMilliseconds
        let reducedMs = max(
            fuzzy(milliseconds: intervalMs / Self.failureIntervalReduction),
            initialDelay.inMilliseconds
        )
        return .milliseconds(reducedMs)
    }

    private func fuzzy(interval: Duration) -> Duration {
        .milliseconds(fuzzy(milliseconds: interval.inMilliseconds))
    }

    private func fuzzy(milliseconds: Int64) -> Int64 {
        let low = Int64(Double(milliseconds) * 0.98)
        let high = Int64(Double(milliseconds) * 1.03)
        guard high > low else { return milliseconds }
        return Int64.random(in: low...high)
    }
}

public enum ServiceDiscoveryProviders {
    public static func create(
        initialDelay: Duration,
        interval: Duration,
        initialClients: [any ServiceApiClient],
        clientFactory: any ServiceApiClientFactory
    ) async throws -> any ServiceDiscoveryProvider {
        let discoveryClient = try extractClient(
            from: initialClients,
            type: (any ServiceDiscoveryClient).self
        )
        let result = try await discoveryClient.latest(isInitialRequest: true)
        switch result {
        case .keepExisting:
            return DisabledServiceDiscoveryProvider(initialClients: initialClients)
        case .switchTo(let endpoints, _):
            let core = clientFactory.create(endpoint: endpoints.core)
            return DefaultServiceDiscoveryProvider(
                initialDelay: initialDelay,
                interval: interval,
                initialClients: [
                    endpoints.core.id: core,
                    endpoints.api.id: clientFactory.create(endpoint: endpoints.api, coreClient: core),
                    endpoints.discovery.id: clientFactory.create(endpoint: endpoints.discovery)
                ],
                clientFactory: clientFactory
            )
        }
    }

    public static func create(
        initialDelay: Duration,
        interval: Duration,
        initialClients: [any ServiceApiClient],
        clientFactory: any ServiceApiClientFactory,
        onCreated: @escaping @Sendable (Result<any ServiceDiscoveryProvider, Error>) -> Void
    ) {
        Task {
            do {
                let provider = try await create(
                    initialDelay: initialDelay,
                    interval: interval,
                    initialClients: initialClients,
                    clientFactory: clientFactory
                )
                onCreated(.success(provider))
            } catch {
                onCreated(.failure(error))
            }
        }
    }

    static func extractClient<T: Sendable>(from clients: [any ServiceApiClient], type: T.Type) throws -> T {
        guard let found = clients.compactMap({ $0 as? T }).first else {
            throw DiscoveryFailure(message: "Service client [\(type)] was not found")
        }
        return found
    }
}

private extension Duration {
    var inMilliseconds: Int64 {
        let (seconds, attoseconds) = components
        return seconds * 1_000 + attoseconds / 1_000_000_000_000_000
    }
}

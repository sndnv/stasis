import Foundation
@testable import StasisClientLib
import Testing

@Suite("ServiceDiscoveryProvider")
struct ServiceDiscoveryProviderTests {
    private final class TestApiClient: ServiceApiClient {}
    private final class TestCoreClient: ServiceApiClient {}

    private struct TestClientFactory: ServiceApiClientFactory {
        let nextDiscoveryResult: ServiceDiscoveryResult

        init(nextDiscoveryResult: ServiceDiscoveryResult = .keepExisting) {
            self.nextDiscoveryResult = nextDiscoveryResult
        }

        func create(endpoint: ServiceApiEndpoint.Api, coreClient: any ServiceApiClient) -> any ServiceApiClient {
            TestApiClient()
        }

        func create(endpoint: ServiceApiEndpoint.Core) -> any ServiceApiClient {
            TestCoreClient()
        }

        func create(endpoint: ServiceApiEndpoint.Discovery) -> any ServiceApiClient {
            MockServiceDiscoveryClient(
                initialDiscoveryResult: nextDiscoveryResult,
                nextDiscoveryResult: nextDiscoveryResult
            )
        }
    }

    private func createClients(
        initialDiscoveryResult: ServiceDiscoveryResult,
        nextDiscoveryResult: ServiceDiscoveryResult
    ) -> [any ServiceApiClient] {
        [
            MockServiceDiscoveryClient(
                initialDiscoveryResult: initialDiscoveryResult,
                nextDiscoveryResult: nextDiscoveryResult
            )
        ]
    }

    private func randomSwitchTo(recreateExisting: Bool = false) -> ServiceDiscoveryResult {
        .switchTo(
            endpoints: ServiceDiscoveryResult.Endpoints(
                api: ServiceApiEndpoint.Api(uri: UUID().uuidString),
                core: ServiceApiEndpoint.Core(address: .http(uri: UUID().uuidString)),
                discovery: ServiceApiEndpoint.Discovery(uri: UUID().uuidString)
            ),
            recreateExisting: recreateExisting
        )
    }

    @Test("supports providing existing clients when discovery is not active")
    func providesExistingWhenDisabled() async throws {
        let provider = try await ServiceDiscoveryProviders.create(
            initialDelay: .seconds(3),
            interval: .seconds(3),
            initialClients: createClients(
                initialDiscoveryResult: .keepExisting,
                nextDiscoveryResult: .keepExisting
            ),
            clientFactory: TestClientFactory()
        )

        await managed(provider) {
            #expect(provider is DisabledServiceDiscoveryProvider)

            let discovery: any ServiceDiscoveryClient = try await provider.latest((any ServiceDiscoveryClient).self)
            #expect(discovery is MockServiceDiscoveryClient)

            await #expect(throws: DiscoveryFailure.self) {
                _ = try await provider.latest(TestApiClient.self)
            }
            await #expect(throws: DiscoveryFailure.self) {
                _ = try await provider.latest(TestCoreClient.self)
            }
        }
    }

    @Test("supports providing new clients when discovery is active")
    func providesNewWhenActive() async throws {
        let provider = try await ServiceDiscoveryProviders.create(
            initialDelay: .seconds(3),
            interval: .seconds(3),
            initialClients: createClients(
                initialDiscoveryResult: .switchTo(
                    endpoints: ServiceDiscoveryResult.Endpoints(
                        api: ServiceApiEndpoint.Api(uri: "test-rui"),
                        core: ServiceApiEndpoint.Core(address: .http(uri: "test-rui")),
                        discovery: ServiceApiEndpoint.Discovery(uri: "test-rui")
                    ),
                    recreateExisting: false
                ),
                nextDiscoveryResult: .keepExisting
            ),
            clientFactory: TestClientFactory()
        )

        await managed(provider) {
            #expect(provider is DefaultServiceDiscoveryProvider)

            let discovery: any ServiceDiscoveryClient = try await provider.latest((any ServiceDiscoveryClient).self)
            #expect(discovery is MockServiceDiscoveryClient)

            _ = try await provider.latest(TestApiClient.self)
            _ = try await provider.latest(TestCoreClient.self)
        }
    }

    @Test("periodically refreshes endpoints (with result=keep-existing)")
    func periodicRefreshKeepExisting() async throws {
        let provider = try await ServiceDiscoveryProviders.create(
            initialDelay: .milliseconds(200),
            interval: .milliseconds(200),
            initialClients: createClients(
                initialDiscoveryResult: randomSwitchTo(),
                nextDiscoveryResult: randomSwitchTo()
            ),
            clientFactory: TestClientFactory()
        )

        await managed(provider) {
            #expect(provider is DefaultServiceDiscoveryProvider)

            let initialDiscovery = try await provider.latest((any ServiceDiscoveryClient).self)
            let initialApi = try await provider.latest(TestApiClient.self)
            let initialCore = try await provider.latest(TestCoreClient.self)

            try await Task.sleep(for: .milliseconds(100))

            #expect(identityEqual(try await provider.latest((any ServiceDiscoveryClient).self), initialDiscovery))
            #expect(identityEqual(try await provider.latest(TestApiClient.self), initialApi))
            #expect(identityEqual(try await provider.latest(TestCoreClient.self), initialCore))
        }
    }

    @Test("periodically refreshes endpoints (with result=switch-to)")
    func periodicRefreshSwitchTo() async throws {
        let provider = try await ServiceDiscoveryProviders.create(
            initialDelay: .milliseconds(200),
            interval: .milliseconds(200),
            initialClients: createClients(
                initialDiscoveryResult: randomSwitchTo(),
                nextDiscoveryResult: randomSwitchTo()
            ),
            clientFactory: TestClientFactory(nextDiscoveryResult: randomSwitchTo(recreateExisting: true))
        )

        await managed(provider) {
            #expect(provider is DefaultServiceDiscoveryProvider)

            let initialDiscovery = try await provider.latest((any ServiceDiscoveryClient).self)
            let initialApi = try await provider.latest(TestApiClient.self)
            let initialCore = try await provider.latest(TestCoreClient.self)

            try await Task.sleep(for: .milliseconds(100))

            #expect(identityEqual(try await provider.latest((any ServiceDiscoveryClient).self), initialDiscovery))
            #expect(identityEqual(try await provider.latest(TestApiClient.self), initialApi))
            #expect(identityEqual(try await provider.latest(TestCoreClient.self), initialCore))

            await eventually {
                guard
                    let d = try? await provider.latest((any ServiceDiscoveryClient).self),
                    let a = try? await provider.latest(TestApiClient.self),
                    let c = try? await provider.latest(TestCoreClient.self)
                else { return false }
                return !identityEqual(d, initialDiscovery)
                    && !identityEqual(a, initialApi)
                    && !identityEqual(c, initialCore)
            }
        }
    }

    @Test("does not recreate clients for same endpoints")
    func doesNotRecreateForSameEndpoints() async throws {
        let provider = try await ServiceDiscoveryProviders.create(
            initialDelay: .milliseconds(100),
            interval: .milliseconds(100),
            initialClients: createClients(
                initialDiscoveryResult: randomSwitchTo(),
                nextDiscoveryResult: randomSwitchTo()
            ),
            clientFactory: TestClientFactory(nextDiscoveryResult: randomSwitchTo())
        )

        await managed(provider) {
            #expect(provider is DefaultServiceDiscoveryProvider)

            let initialDiscovery = try await provider.latest((any ServiceDiscoveryClient).self)
            let initialApi = try await provider.latest(TestApiClient.self)
            let initialCore = try await provider.latest(TestCoreClient.self)

            try await Task.sleep(for: .milliseconds(200))

            #expect(!identityEqual(try await provider.latest((any ServiceDiscoveryClient).self), initialDiscovery))
            #expect(!identityEqual(try await provider.latest(TestApiClient.self), initialApi))
            #expect(!identityEqual(try await provider.latest(TestCoreClient.self), initialCore))

            let latestDiscovery = try await provider.latest((any ServiceDiscoveryClient).self)
            let latestApi = try await provider.latest(TestApiClient.self)
            let latestCore = try await provider.latest(TestCoreClient.self)

            try await Task.sleep(for: .milliseconds(200))

            await eventually {
                guard
                    let d = try? await provider.latest((any ServiceDiscoveryClient).self),
                    let a = try? await provider.latest(TestApiClient.self),
                    let c = try? await provider.latest(TestCoreClient.self)
                else { return false }
                return identityEqual(d, latestDiscovery)
                    && identityEqual(a, latestApi)
                    && identityEqual(c, latestCore)
            }
        }
    }

    @Test("fails to retrieve unsupported client types")
    func failsForUnsupportedTypes() async throws {
        let provider = try await ServiceDiscoveryProviders.create(
            initialDelay: .seconds(3),
            interval: .seconds(3),
            initialClients: createClients(
                initialDiscoveryResult: .keepExisting,
                nextDiscoveryResult: .keepExisting
            ),
            clientFactory: TestClientFactory()
        )

        await managed(provider) {
            #expect(provider is DisabledServiceDiscoveryProvider)

            do {
                _ = try await provider.latest(TestApiClient.self)
                Issue.record("expected DiscoveryFailure")
            } catch let failure as DiscoveryFailure {
                #expect(failure.message == "Service client [\(TestApiClient.self)] was not found")
            }
        }
    }

    @Test("handles discovery failures")
    func handlesDiscoveryFailures() async throws {
        let clientCalls = Counter()

        struct FailingFactory: ServiceApiClientFactory {
            let counter: Counter
            func create(endpoint: ServiceApiEndpoint.Api, coreClient: any ServiceApiClient) -> any ServiceApiClient {
                TestApiClient()
            }
            func create(endpoint: ServiceApiEndpoint.Core) -> any ServiceApiClient {
                TestCoreClient()
            }
            func create(endpoint: ServiceApiEndpoint.Discovery) -> any ServiceApiClient {
                FailingDiscoveryClient(counter: counter)
            }
        }

        let provider = try await ServiceDiscoveryProviders.create(
            initialDelay: .milliseconds(100),
            interval: .milliseconds(200),
            initialClients: createClients(
                initialDiscoveryResult: .switchTo(
                    endpoints: ServiceDiscoveryResult.Endpoints(
                        api: ServiceApiEndpoint.Api(uri: "test-rui"),
                        core: ServiceApiEndpoint.Core(address: .http(uri: "test-rui")),
                        discovery: ServiceApiEndpoint.Discovery(uri: "test-rui")
                    ),
                    recreateExisting: false
                ),
                nextDiscoveryResult: .keepExisting
            ),
            clientFactory: FailingFactory(counter: clientCalls)
        )

        await managed(provider) {
            #expect(await clientCalls.value == 0)

            try await Task.sleep(for: .milliseconds(100))

            #expect(await clientCalls.value == 0)

            try await Task.sleep(for: .milliseconds(400))

            #expect(await clientCalls.value >= 3)
        }
    }

    @Test("supports creating the provider asynchronously")
    func supportsAsyncCreation() async throws {
        let providerRef = ProviderRef()

        ServiceDiscoveryProviders.create(
            initialDelay: .seconds(3),
            interval: .seconds(3),
            initialClients: createClients(
                initialDiscoveryResult: .keepExisting,
                nextDiscoveryResult: .keepExisting
            ),
            clientFactory: TestClientFactory(),
            onCreated: { result in
                Task { await providerRef.set(result) }
            }
        )

        #expect(await providerRef.value == nil)

        await eventually {
            guard let result = await providerRef.value,
                  case .success(let p) = result
            else { return false }
            await p.stop()
            return p is DisabledServiceDiscoveryProvider
        }
    }

    private final class FailingDiscoveryClient: ServiceDiscoveryClient {
        let attributes: any ServiceDiscoveryClientAttributes
        let counter: Counter

        init(counter: Counter) {
            self.attributes = MockServiceDiscoveryClient.TestAttributes(a: "b")
            self.counter = counter
        }

        func latest(isInitialRequest: Bool) async throws -> ServiceDiscoveryResult {
            await counter.increment()
            throw DiscoveryFailure(message: "Test failure")
        }
    }

    private actor Counter {
        private(set) var value: Int = 0
        func increment() { value += 1 }
    }

    private actor ProviderRef {
        var value: Result<any ServiceDiscoveryProvider, Error>?
        func set(_ value: Result<any ServiceDiscoveryProvider, Error>) {
            self.value = value
        }
    }

    private func managed(
        _ provider: any ServiceDiscoveryProvider,
        _ block: () async throws -> Void
    ) async {
        do {
            try await block()
        } catch {
            Issue.record("managed block failed: \(error)")
        }
        await provider.stop()
    }

    private func identityEqual(_ lhs: Any, _ rhs: Any) -> Bool {
        (lhs as AnyObject) === (rhs as AnyObject)
    }

    private func eventually(
        timeout: Duration = .seconds(5),
        interval: Duration = .milliseconds(50),
        _ check: () async -> Bool
    ) async {
        let deadline = ContinuousClock.now.advanced(by: timeout)
        while ContinuousClock.now < deadline {
            if await check() { return }
            try? await Task.sleep(for: interval)
        }
        Issue.record("eventually condition did not become true within \(timeout)")
    }
}

import Foundation
@testable import StasisClientLib
import Testing

@Suite("DefaultServerApiEndpointClient")
struct DefaultServerApiEndpointClientTests {
    private let server = "http://localhost:1234"
    private let apiCredentials = HttpCredentials.basic(username: "some-user", password: "some-password")

    private func makeClient(
        selfDevice: DeviceId = UUID(),
        decryption: DecryptionContext = .disabled
    ) -> (DefaultServerApiEndpointClient, HttpTransportStub) {
        let stub = HttpTransportStub()
        let http = HttpClient(
            transport: stub,
            credentialsProvider: StaticCredentialsProvider(apiCredentials),
            retryConfig: .disabled
        )
        return (
            DefaultServerApiEndpointClient(
                serverApiUrl: server,
                selfDevice: selfDevice,
                http: http,
                decryption: decryption
            ),
            stub
        )
    }

    private static let now: Date = {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: "2026-05-23T12:34:56Z")!
    }()

    private static func definition(device: DeviceId = UUID()) -> DatasetDefinition {
        DatasetDefinition(
            id: UUID(),
            info: "test-definition",
            device: device,
            redundantCopies: 1,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3)),
            created: now,
            updated: now
        )
    }

    private static func entry(definition: DatasetDefinitionId = UUID()) -> DatasetEntry {
        DatasetEntry(
            id: UUID(),
            definition: definition,
            device: UUID(),
            data: [UUID(), UUID()],
            metadata: UUID(),
            changes: 0,
            size: 0,
            created: now
        )
    }

    private static func schedule() -> Schedule {
        Schedule(
            id: UUID(),
            info: "test-schedule",
            isPublic: true,
            start: LocalDateTime("2026-05-23T12:34:56"),
            interval: SecondsDuration(60),
            created: now,
            updated: now
        )
    }

    private static func deviceFixture() -> Device {
        Device(
            id: UUID(),
            name: "test-device",
            node: UUID(),
            owner: UUID(),
            active: true,
            limits: nil,
            created: now,
            updated: now
        )
    }

    private static func userFixture() -> User {
        User(
            id: UUID(),
            salt: "test-salt",
            active: true,
            limits: nil,
            permissions: [],
            created: now,
            updated: now
        )
    }

    @Test("creates dataset definitions")
    func createsDatasetDefinitions() async throws {
        let device = UUID()
        let (client, stub) = makeClient(selfDevice: device)
        let expected = CreatedDatasetDefinition(definition: UUID())
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let request = CreateDatasetDefinition(
            info: "test-definition",
            device: device,
            redundantCopies: 1,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3))
        )
        let created = try await client.createDatasetDefinition(request: request)
        #expect(created == expected)

        let recorded = await stub.recordedRequests()
        let actual = try #require(recorded.first)
        #expect(actual.httpMethod == "POST")
        #expect(actual.url?.path == "/v1/datasets/definitions/own")
        let decoded = try JSONCoders.decoder().decode(CreateDatasetDefinition.self, from: actual.httpBody ?? Data())
        #expect(decoded == request)
    }

    @Test("fails to create dataset definitions for a different device")
    func failsToCreateForDifferentDevice() async throws {
        let (client, _) = makeClient()
        let otherDevice = UUID()
        let request = CreateDatasetDefinition(
            info: "test-definition",
            device: otherDevice,
            redundantCopies: 1,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3))
        )

        do {
            _ = try await client.createDatasetDefinition(request: request)
            Issue.record("Expected EndpointFailure")
        } catch let failure as EndpointFailure {
            #expect(failure.message.contains("Cannot create dataset definition for a different device"))
            #expect(failure.message.contains(otherDevice.uuidString.lowercased()))
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("updates dataset definitions")
    func updatesDatasetDefinitions() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 200))

        let id = UUID()
        let request = UpdateDatasetDefinition(
            info: "test-definition",
            redundantCopies: 1,
            existingVersions: .init(policy: .all, duration: SecondsDuration(3)),
            removedVersions: .init(policy: .all, duration: SecondsDuration(3))
        )
        try await client.updateDatasetDefinition(definition: id, request: request)

        let recorded = await stub.recordedRequests()
        let actual = try #require(recorded.first)
        #expect(actual.httpMethod == "PUT")
        #expect(actual.url?.path == "/v1/datasets/definitions/own/\(id.uuidString.lowercased())")
        let decoded = try JSONCoders.decoder().decode(UpdateDatasetDefinition.self, from: actual.httpBody ?? Data())
        #expect(decoded == request)
    }

    @Test("deletes dataset definitions")
    func deletesDatasetDefinitions() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 200))

        let id = UUID()
        try await client.deleteDatasetDefinition(definition: id)

        let recorded = await stub.recordedRequests()
        let actual = try #require(recorded.first)
        #expect(actual.httpMethod == "DELETE")
        #expect(actual.url?.path == "/v1/datasets/definitions/own/\(id.uuidString.lowercased())")
    }

    @Test("retrieves dataset definitions")
    func retrievesDatasetDefinitions() async throws {
        let device = UUID()
        let (client, stub) = makeClient(selfDevice: device)
        let expected = [Self.definition(device: device), Self.definition(device: device)]
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.datasetDefinitions()
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.httpMethod == "GET")
        #expect(recorded.first?.url?.path == "/v1/datasets/definitions/own")
    }

    @Test("filters out dataset definitions not for the current device")
    func filtersOtherDevices() async throws {
        let device = UUID()
        let (client, stub) = makeClient(selfDevice: device)
        let mine = Self.definition(device: device)
        let theirs = Self.definition()
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode([theirs, mine])))

        let actual = try await client.datasetDefinitions()
        #expect(actual == [mine])
    }

    @Test("retrieves individual dataset definitions")
    func retrievesIndividualDefinition() async throws {
        let device = UUID()
        let (client, stub) = makeClient(selfDevice: device)
        let expected = Self.definition(device: device)
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.datasetDefinition(definition: expected.id)
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.url?.path == "/v1/datasets/definitions/own/\(expected.id.uuidString.lowercased())")
    }

    @Test("fails to retrieve individual dataset definitions for a different device")
    func failsToRetrieveForDifferentDevice() async throws {
        let (client, stub) = makeClient()
        let other = Self.definition()
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(other)))

        do {
            _ = try await client.datasetDefinition(definition: other.id)
            Issue.record("Expected EndpointFailure")
        } catch let failure as EndpointFailure {
            #expect(failure.message == "Cannot retrieve dataset definition for a different device")
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("creates dataset entries")
    func createsDatasetEntries() async throws {
        let (client, stub) = makeClient()
        let expected = CreatedDatasetEntry(entry: UUID())
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let request = CreateDatasetEntry(
            definition: UUID(),
            device: UUID(),
            data: [UUID(), UUID()],
            metadata: UUID(),
            changes: 1,
            size: 2
        )
        let created = try await client.createDatasetEntry(request: request)
        #expect(created == expected)

        let recorded = await stub.recordedRequests()
        let actual = try #require(recorded.first)
        #expect(actual.httpMethod == "POST")
        #expect(actual.url?.path == "/v1/datasets/entries/own/for-definition/\(request.definition.uuidString.lowercased())")
        let decoded = try JSONCoders.decoder().decode(CreateDatasetEntry.self, from: actual.httpBody ?? Data())
        #expect(decoded == request)
    }

    @Test("deletes dataset entries")
    func deletesDatasetEntries() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 200))

        let id = UUID()
        try await client.deleteDatasetEntry(entry: id)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.httpMethod == "DELETE")
        #expect(recorded.first?.url?.path == "/v1/datasets/entries/own/\(id.uuidString.lowercased())")
    }

    @Test("retrieves dataset entries for a definition")
    func retrievesEntriesForDefinition() async throws {
        let definition = UUID()
        let (client, stub) = makeClient()
        let expected = [Self.entry(definition: definition), Self.entry(definition: definition)]
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.datasetEntries(definition: definition)
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.url?.path == "/v1/datasets/entries/own/for-definition/\(definition.uuidString.lowercased())")
    }

    @Test("retrieves individual dataset entries")
    func retrievesIndividualEntry() async throws {
        let (client, stub) = makeClient()
        let expected = Self.entry()
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.datasetEntry(entry: expected.id)
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.url?.path == "/v1/datasets/entries/own/\(expected.id.uuidString.lowercased())")
    }

    @Test("retrieves the latest dataset entry for a definition")
    func retrievesLatestEntry() async throws {
        let definition = UUID()
        let (client, stub) = makeClient()
        let expected = Self.entry(definition: definition)
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.latestEntry(definition: definition, until: nil)
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.url?.path == "/v1/datasets/entries/own/for-definition/\(definition.uuidString.lowercased())/latest")
    }

    @Test("returns nil for missing latest dataset entry")
    func returnsNilForMissingLatest() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 404))

        let actual = try await client.latestEntry(definition: UUID(), until: nil)
        #expect(actual == nil)
    }

    @Test("retrieves the latest dataset entry until a timestamp")
    func retrievesLatestEntryUntil() async throws {
        let definition = UUID()
        let (client, stub) = makeClient()
        let expected = Self.entry(definition: definition)
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let until = Self.now
        _ = try await client.latestEntry(definition: definition, until: until)

        let recorded = await stub.recordedRequests()
        let url = try #require(recorded.first?.url)
        #expect(url.path == "/v1/datasets/entries/own/for-definition/\(definition.uuidString.lowercased())/latest")
        #expect((url.query ?? "").contains("until="))
    }

    @Test("retrieves public schedules")
    func retrievesPublicSchedules() async throws {
        let (client, stub) = makeClient()
        let expected = [Self.schedule(), Self.schedule()]
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.publicSchedules()
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.url?.path == "/v1/schedules/public")
    }

    @Test("retrieves individual public schedules")
    func retrievesIndividualSchedule() async throws {
        let (client, stub) = makeClient()
        let expected = Self.schedule()
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.publicSchedule(schedule: expected.id)
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.url?.path == "/v1/schedules/public/\(expected.id.uuidString.lowercased())")
    }

    @Test("fails to retrieve dataset metadata without a decryption context (with entry)")
    func failsToRetrieveMetadataWithEntry() async throws {
        let (client, stub) = makeClient(decryption: .disabled)
        let resolved = Self.entry()
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(resolved)))

        do {
            _ = try await client.datasetMetadata(entry: resolved)
            Issue.record("Expected EndpointFailure")
        } catch let failure as EndpointFailure {
            #expect(failure.message == "Cannot retrieve dataset metadata; decryption context is disabled")
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("fails to retrieve dataset metadata without a decryption context (with entry ID)")
    func failsToRetrieveMetadataWithEntryId() async throws {
        let (client, stub) = makeClient(decryption: .disabled)
        let resolved = Self.entry()
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(resolved)))

        do {
            _ = try await client.datasetMetadata(entry: resolved.id)
            Issue.record("Expected EndpointFailure")
        } catch let failure as EndpointFailure {
            #expect(failure.message == "Cannot retrieve dataset metadata; decryption context is disabled")
        } catch {
            Issue.record("Unexpected error: \(error)")
        }
    }

    @Test("retrieves current user")
    func retrievesCurrentUser() async throws {
        let (client, stub) = makeClient()
        let expected = Self.userFixture()
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.user()
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.url?.path == "/v1/users/self")
    }

    @Test("resets the current user's salt")
    func resetsUserSalt() async throws {
        let (client, stub) = makeClient()
        let expected = UpdatedUserSalt(salt: "test-salt")
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.resetUserSalt()
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        let request = try #require(recorded.first)
        #expect(request.httpMethod == "PUT")
        #expect(request.url?.path == "/v1/users/self/salt")
        #expect((request.httpBody ?? Data()).isEmpty)
    }

    @Test("updates the current user's password")
    func updatesUserPassword() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 200))

        try await client.resetUserPassword(request: ResetUserPassword(rawPassword: "test-password"))

        let recorded = await stub.recordedRequests()
        let request = try #require(recorded.first)
        #expect(request.httpMethod == "PUT")
        #expect(request.url?.path == "/v1/users/self/password")
        let body = String(data: request.httpBody ?? Data(), encoding: .utf8)
        #expect(body == """
        {"raw_password":"test-password"}
        """)
    }

    @Test("retrieves current device")
    func retrievesCurrentDevice() async throws {
        let device = UUID()
        let (client, stub) = makeClient(selfDevice: device)
        let expected = Self.deviceFixture()
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.device()
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.url?.path == "/v1/devices/own/\(device.uuidString.lowercased())")
    }

    @Test("pushes current device key")
    func pushesDeviceKey() async throws {
        let device = UUID()
        let (client, stub) = makeClient(selfDevice: device)
        await stub.enqueue(.init(statusCode: 200))

        try await client.pushDeviceKey(key: Data("test-key".utf8))

        let recorded = await stub.recordedRequests()
        let request = try #require(recorded.first)
        #expect(request.httpMethod == "PUT")
        #expect(request.url?.path == "/v1/devices/own/\(device.uuidString.lowercased())/key")
        #expect(request.value(forHTTPHeaderField: "Content-Type") == "application/octet-stream")
        #expect(request.httpBody == Data("test-key".utf8))
    }

    @Test("pulls current device key")
    func pullsDeviceKey() async throws {
        let device = UUID()
        let (client, stub) = makeClient(selfDevice: device)
        let expected = Data("test-key".utf8)
        await stub.enqueue(.init(statusCode: 200, body: expected))

        let actual = try await client.pullDeviceKey()
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.url?.path == "/v1/devices/own/\(device.uuidString.lowercased())/key")
    }

    @Test("fails to pull missing device keys")
    func failsToPullMissingKey() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 200, body: Data()))

        await #expect(throws: ResourceMissingFailure.self) {
            _ = try await client.pullDeviceKey()
        }
    }

    @Test("checks current device key — existing")
    func deviceKeyExistsExisting() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 200))

        let exists = try await client.deviceKeyExists()
        #expect(exists)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.httpMethod == "HEAD")
    }

    @Test("checks current device key — missing")
    func deviceKeyExistsMissing() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 404))

        let exists = try await client.deviceKeyExists()
        #expect(!exists)
    }

    @Test("handles failures when checking device keys")
    func deviceKeyExistsFailure() async throws {
        let (client, stub) = makeClient()
        await stub.enqueue(.init(statusCode: 505))

        await #expect(throws: EndpointFailure.self) {
            _ = try await client.deviceKeyExists()
        }
    }

    @Test("makes ping requests")
    func ping() async throws {
        let (client, stub) = makeClient()
        let expected = Ping(id: UUID())
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.ping()
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        #expect(recorded.first?.url?.path == "/v1/service/ping")
    }

    @Test("retrieves all commands")
    func retrievesAllCommands() async throws {
        let device = UUID()
        let (client, stub) = makeClient(selfDevice: device)
        let expected = [
            CommandAsJson(
                sequenceId: 1, source: "user", target: nil,
                parameters: .init(logoutUser: nil), created: Self.now
            ),
            CommandAsJson(
                sequenceId: 2, source: "service", target: device,
                parameters: .init(logoutUser: .init(reason: "reason")), created: Self.now
            )
        ]
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(expected)))

        let actual = try await client.commands(lastSequenceId: nil)
        #expect(actual == expected)

        let recorded = await stub.recordedRequests()
        let url = try #require(recorded.first?.url)
        #expect(url.path == "/v1/devices/own/\(device.uuidString.lowercased())/commands")
        #expect(url.query == nil)
    }

    @Test("retrieves unprocessed commands")
    func retrievesUnprocessedCommands() async throws {
        let device = UUID()
        let (client, stub) = makeClient(selfDevice: device)
        await stub.enqueue(.init(statusCode: 200, body: Data("[]".utf8)))

        _ = try await client.commands(lastSequenceId: 42)

        let recorded = await stub.recordedRequests()
        let url = try #require(recorded.first?.url)
        #expect(url.path == "/v1/devices/own/\(device.uuidString.lowercased())/commands")
        #expect(url.query == "last_sequence_id=42")
    }

    @Test("sends analytics entries")
    func sendsAnalyticsEntry() async throws {
        let (client, stub) = makeClient()
        let response = CreatedAnalyticsEntry(entry: UUID())
        await stub.enqueue(.init(statusCode: 200, body: try JSONCoders.encoder().encode(response)))

        let entry = AnalyticsEntry.collected(.init(app: NoApplicationInformation()))

        try await client.sendAnalyticsEntry(entry)

        let recorded = await stub.recordedRequests()
        let request = try #require(recorded.first)
        #expect(request.httpMethod == "POST")
        #expect(request.url?.path == "/v1/analytics")
    }
}
